package org.example.productservice.service;

import lombok.RequiredArgsConstructor;

import org.example.productservice.dto.InternalPolicyResponse;
import org.example.productservice.client.TriggerServiceClient;
import org.example.productservice.client.FinancialServiceClient;
import org.example.productservice.dto.*;
import org.example.productservice.exception.ConflictException;
import org.example.productservice.exception.NotFoundException;
import org.example.productservice.exception.ValidationException;
import org.example.productservice.model.*;
import org.example.productservice.repository.PolicyRepository;
import org.example.productservice.repository.PolicyStatusHistoryRepository;
import org.example.productservice.repository.PolicyTriggerSnapshotRepository;
import org.example.productservice.repository.ProductRepository;
import org.example.productservice.util.TriggerDescriptionGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PolicyService{
    private final PolicyRepository policyRepository;
    private final ProductRepository productRepository;
    private final PolicyTriggerSnapshotRepository snapshotRepository;
    private final PolicyStatusHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final TriggerServiceClient triggerServiceClient;
    private final FinancialServiceClient financialServiceClient;
    private final ProductService productService;

    @Transactional
    public Mono<PolicyResponse> createPolicy(
            Long customerId,
            CreatePolicyRequest request
    ){
        validateCreateRequest(customerId,request);

        return validateQuoteIfPresent(customerId,request)
                .then(productRepository.findByIdAndStatus(
                        request.getProductId(),
                        ProductStatus.ACTIVE.name()
                ))
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Active product not found")
                ))
                .flatMap(product->{
                    validateSumInsured(product,request.getSumInsured());
                    validatePolicyTerm(product,request.getTermMonths());

                    return policyRepository
                            .existsByCustomerIdAndProductIdAndStatus(
                                    customerId,
                                    product.getId(),
                                    PolicyStatus.PENDING.name()
                            )
                            .flatMap(exists->{
                                if(exists){
                                    return Mono.error(new ConflictException(
                                            "A pending policy application already exists for this product"
                                    ));
                                }

                                LocalDateTime now=LocalDateTime.now();

                                Policy policy=Policy.builder()
                                        .policyNumber(generatePolicyNumber())
                                        .customerId(customerId)
                                        .productId(product.getId())
                                        .productCode(product.getProductCode())
                                        .productName(product.getName())
                                        .locationZoneId(request.getLocationZoneId())
                                        .sumInsured(request.getSumInsured())
                                        .premiumAmount(calculateTemporaryPremium(
                                                request.getSumInsured(),
                                                request.getTermMonths()
                                        ))
                                        .termMonths(request.getTermMonths())
                                        .status(PolicyStatus.PENDING.name())
                                        .createdAt(now)
                                        .updatedAt(now)
                                        .build();

                                return policyRepository.save(policy)
                                        .flatMap(saved->{
                                            Mono<Void> historyMono=recordHistory(
                                                    saved.getId(),
                                                    null,
                                                    PolicyStatus.PENDING.name(),
                                                    customerId,
                                                    null
                                            );

                                            return resolveSnapshot(product,saved.getId(),request)
                                                    .flatMap(snapshot->{
                                                        saved.setTriggerSnapshotId(
                                                                snapshot.getId()
                                                        );
                                                        return policyRepository.save(saved);
                                                    })
                                                    .defaultIfEmpty(saved)
                                                    .flatMap(updated->historyMono
                                                            .then(Mono.just(updated)))
                                                    .flatMap(updated->consumeQuoteIfPresent(
                                                            request,updated
                                                    ).thenReturn(updated));
                                        });
                            });
                })
                .map(this::toResponse)
                .flatMap(resp -> notificationService.notifyPolicySubmitted(
                        customerId, resp.getId(), resp.getPolicyNumber()
                ).then(notificationService.notifyPolicyPendingReview(
                        resp.getId(), resp.getPolicyNumber()
                )).thenReturn(resp))
                .flatMap(resp -> notificationService.audit(
                        "POLICY_APPLIED", "POLICY", resp.getId(), null,
                        "Applied for policy " + resp.getPolicyNumber() +
                                " (sum insured " + request.getSumInsured() +
                                ", term " + request.getTermMonths() + " months)"
                ).thenReturn(resp));
    }

    @Transactional
    public Mono<PolicyResponse> createCustomPolicy(
            Long customerId,
            CreateCustomPolicyRequest request
    ){
        validateCustomerId(customerId);
        validateCustomPolicyRequest(request);

        ProductRequest productRequest=buildCustomProductRequest(customerId,request);

        return productService.createCustomerDefinedProduct(customerId,productRequest)
                .flatMap(product->{
                    CreatePolicyRequest policyRequest=CreatePolicyRequest.builder()
                            .productId(product.getId())
                            .sumInsured(request.getSumInsured())
                            .termMonths(request.getTermMonths())
                            .locationZoneId(request.getLocationZoneId())
                            .perilCode(request.getPerilCode())
                            .triggerMetric(request.getMeasurementType())
                            .triggerOperator(request.getComparisonOperator())
                            .triggerThreshold(request.getThresholdValue())
                            .triggerDurationHours(request.getObservationWindowHours())
                            .triggerAggregationType(request.getAggregationType())
                            .triggerMinObservationCount(request.getMinimumObservationCount())
                            .build();

                    return createPolicy(customerId,policyRequest);
                });
    }

    private void validateCustomPolicyRequest(CreateCustomPolicyRequest request){
        if(request==null){
            throw new ValidationException("Custom policy request is required");
        }

        if(request.getLocationZoneId()==null||request.getLocationZoneId()<=0){
            throw new ValidationException(
                    "Location zone ID must be greater than zero"
            );
        }

        if(request.getSumInsured()==null||request.getSumInsured().signum()<=0){
            throw new ValidationException(
                    "Sum insured must be greater than zero"
            );
        }

        if(request.getTermMonths()==null||request.getTermMonths()<=0){
            throw new ValidationException(
                    "Policy term must be greater than zero"
            );
        }
    }

    private ProductRequest buildCustomProductRequest(
            Long customerId,
            CreateCustomPolicyRequest request
    ){
        String generatedCode="CUSTOM-"+customerId+"-"+
                UUID.randomUUID().toString()
                        .substring(0,8)
                        .toUpperCase(Locale.ROOT);

        String name=StringUtils.hasText(request.getName())&&
                request.getName().trim().length()>=3
                ?request.getName().trim()
                :"Custom Policy - "+(
                        StringUtils.hasText(request.getPerilCode())
                                ?request.getPerilCode()
                                :"Custom"
                );

        return ProductRequest.builder()
                .productCode(generatedCode)
                .name(name)
                .description(request.getDescription())
                .category(request.getCategory())
                .minimumSumInsured(request.getSumInsured())
                .maximumSumInsured(request.getSumInsured())
                .minimumTermMonths(request.getTermMonths())
                .maximumTermMonths(request.getTermMonths())
                .perilCode(request.getPerilCode())
                .measurementType(request.getMeasurementType())
                .measurementUnit(request.getMeasurementUnit())
                .thresholdValue(request.getThresholdValue())
                .comparisonOperator(request.getComparisonOperator())
                .aggregationType(request.getAggregationType())
                .observationWindowHours(request.getObservationWindowHours())
                .minimumObservationCount(request.getMinimumObservationCount())
                .triggerDescription(request.getTriggerDescription())
                .build();
    }

    public Flux<PolicyResponse> getMyPolicies(
            Long customerId,
            String status
    ){
        validateCustomerId(customerId);

        if(status!=null&&!status.isBlank()){
            return policyRepository
                    .findAllByCustomerIdAndStatus(
                            customerId,
                            validateStatus(status)
                    )
                    .map(this::toResponse);
        }

        return policyRepository.findAllByCustomerId(customerId)
                .map(this::toResponse);
    }

    public Mono<PolicyResponse> getMyPolicy(
            Long customerId,
            Long policyId
    ){
        validateCustomerId(customerId);
        validatePolicyId(policyId);

        return policyRepository.findByIdAndCustomerId(policyId,customerId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Policy not found")
                ))
                .map(this::toResponse);
    }

    @Transactional
    public Mono<MessageResponse> cancelPolicy(
            Long customerId,
            Long policyId
    ){
        validateCustomerId(customerId);
        validatePolicyId(policyId);

        return policyRepository.findByIdAndCustomerId(policyId,customerId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Policy not found")
                ))
                .flatMap(policy->{
                    requireStatus(
                            policy,
                            PolicyStatus.PENDING,
                            "Only pending policies can be cancelled"
                    );

                    String oldStatus=policy.getStatus();
                    policy.setStatus(PolicyStatus.CANCELLED.name());
                    policy.setUpdatedAt(LocalDateTime.now());

                    return policyRepository.save(policy)
                            .flatMap(saved->recordHistory(
                                    saved.getId(),
                                    oldStatus,
                                    PolicyStatus.CANCELLED.name(),
                                    customerId,
                                    null
                            ).thenReturn(saved));
                })
                .flatMap(saved -> notificationService.notifyPolicyCancelled(
                        customerId, saved.getId(), saved.getPolicyNumber()
                ).thenReturn(saved))
                .flatMap(saved -> notificationService.audit(
                        "POLICY_CANCELLED", "POLICY", saved.getId(), null,
                        "Cancelled policy application " + saved.getPolicyNumber()
                ).thenReturn(saved))
                .thenReturn(MessageResponse.builder()
                        .message("Policy cancelled successfully")
                        .build());
    }

    public Flux<PolicyResponse> getPendingPolicies(){
        return policyRepository
                .findAllByStatus(PolicyStatus.PENDING.name())
                .map(this::toResponse);
    }

    public Flux<PolicyResponse> getAllPolicies(String status,Long customerId){
        Flux<Policy> policies=(status!=null&&!status.isBlank())
                ?policyRepository.findAllByStatus(validateStatus(status))
                :policyRepository.findAll();

        if(customerId!=null){
            policies=policies.filter(policy->customerId.equals(policy.getCustomerId()));
        }

        return policies.map(this::toResponse);
    }

    public Mono<PolicyResponse> getPolicy(Long policyId){
        validatePolicyId(policyId);

        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Policy not found")
                ))
                .map(this::toResponse);
    }

    @Transactional
    public Mono<PolicyResponse> approvePolicy(
            Long reviewerId,
            Long policyId
    ){
        validateReviewerId(reviewerId);
        validatePolicyId(policyId);

        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Policy not found")
                ))
                .flatMap(policy->{
                    requireStatus(
                            policy,
                            PolicyStatus.PENDING,
                            "Only pending policies can be approved"
                    );

                    if(policy.getLocationZoneId()==null||
                            policy.getLocationZoneId()<=0){
                        return Mono.error(new ValidationException(
                                "Policy location zone is unavailable"
                        ));
                    }

                    LocalDateTime now=LocalDateTime.now();
                    LocalDate startDate=LocalDate.now();
                    String oldStatus=policy.getStatus();

                    policy.setStatus(PolicyStatus.ACTIVE.name());
                    policy.setStartDate(startDate);
                    policy.setEndDate(
                            startDate.plusMonths(policy.getTermMonths())
                    );
                    policy.setRejectionReason(null);
                    policy.setReviewedBy(reviewerId);
                    policy.setReviewedAt(now);
                    policy.setUpdatedAt(now);

                    return policyRepository.save(policy)
                            .flatMap(saved->recordHistory(
                                    saved.getId(),
                                    oldStatus,
                                    PolicyStatus.ACTIVE.name(),
                                    reviewerId,
                                    null
                            ).thenReturn(saved));
                })
                .flatMap(saved -> notificationService.notifyPolicyApproved(
                        saved.getCustomerId(), saved.getId(), saved.getPolicyNumber()
                ).thenReturn(saved))
                .flatMap(saved -> notificationService.audit(
                        "POLICY_APPROVED", "POLICY", saved.getId(), reviewerId,
                        "Approved policy " + saved.getPolicyNumber()
                ).thenReturn(saved))
                .map(this::toResponse);
    }

    @Transactional
    public Mono<PolicyResponse> rejectPolicy(
            Long reviewerId,
            Long policyId,
            PolicyDecisionRequest request
    ){
        validateReviewerId(reviewerId);
        validatePolicyId(policyId);

        if(request==null||
                request.getReason()==null||
                request.getReason().isBlank()){
            return Mono.error(new ValidationException(
                    "Rejection reason is required"
            ));
        }

        if(request.getReason().trim().length()>500){
            return Mono.error(new ValidationException(
                    "Rejection reason cannot exceed 500 characters"
            ));
        }

        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Policy not found")
                ))
                .flatMap(policy->{
                    requireStatus(
                            policy,
                            PolicyStatus.PENDING,
                            "Only pending policies can be rejected"
                    );

                    LocalDateTime now=LocalDateTime.now();
                    String oldStatus=policy.getStatus();
                    Long custId=policy.getCustomerId();
                    String polNum=policy.getPolicyNumber();

                    policy.setStatus(PolicyStatus.REJECTED.name());
                    policy.setRejectionReason(request.getReason().trim());
                    policy.setReviewedBy(reviewerId);
                    policy.setReviewedAt(now);
                    policy.setUpdatedAt(now);

                    return policyRepository.save(policy)
                            .flatMap(saved->recordHistory(
                                    saved.getId(),
                                    oldStatus,
                                    PolicyStatus.REJECTED.name(),
                                    reviewerId,
                                    request.getReason().trim()
                            ).thenReturn(saved));
                })
                .flatMap(saved -> notificationService.notifyPolicyRejected(
                        saved.getCustomerId(), saved.getId(),
                        saved.getPolicyNumber(), request.getReason().trim()
                ).thenReturn(saved))
                .flatMap(saved -> notificationService.audit(
                        "POLICY_REJECTED", "POLICY", saved.getId(), reviewerId,
                        "Rejected policy " + saved.getPolicyNumber() +
                                ": " + request.getReason().trim()
                ).thenReturn(saved))
                .map(this::toResponse);
    }

    public Mono<InternalPolicyResponse> getInternalPolicy(Long policyId){
        validatePolicyId(policyId);

        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Policy not found")
                ))
                .flatMap(policy->snapshotRepository
                        .findByPolicyId(policyId)
                        .defaultIfEmpty(new PolicyTriggerSnapshot())
                        .map(snapshot->toInternalResponse(policy,snapshot)));
    }

    private InternalPolicyResponse toInternalResponse(
            Policy policy,
            PolicyTriggerSnapshot snapshot
    ){
        return InternalPolicyResponse.builder()
                .id(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .customerId(policy.getCustomerId())
                .productId(policy.getProductId())
                .productCode(policy.getProductCode())
                .productName(policy.getProductName())
                .locationZoneId(policy.getLocationZoneId())
                .sumInsured(policy.getSumInsured())
                .premiumAmount(policy.getPremiumAmount())
                .startDate(policy.getStartDate())
                .endDate(policy.getEndDate())
                .status(policy.getStatus())
                .snapshotMeasurementType(snapshot.getMeasurementType())
                .snapshotMeasurementUnit(snapshot.getMeasurementUnit())
                .snapshotThresholdValue(snapshot.getThresholdValue())
                .snapshotComparisonOperator(snapshot.getComparisonOperator())
                .snapshotAggregationType(snapshot.getAggregationType())
                .snapshotObservationWindowHours(
                        snapshot.getObservationWindowHours())
                .snapshotMinimumObservationCount(
                        snapshot.getMinimumObservationCount())
                .build();
    }

    public Flux<PolicyStatusHistoryResponse> getPolicyHistory(Long policyId){
        validatePolicyId(policyId);

        return policyRepository.existsById(policyId)
                .flatMapMany(exists->{
                    if(!exists){
                        return Flux.error(new NotFoundException("Policy not found"));
                    }
                    return historyRepository
                            .findAllByPolicyIdOrderByChangedAtDesc(policyId)
                            .map(this::toHistoryResponse);
                });
    }

    public Mono<PolicyTriggerSnapshotResponse> getPolicySnapshot(Long policyId){
        validatePolicyId(policyId);

        return policyRepository.existsById(policyId)
                .flatMap(exists->{
                    if(!exists){
                        return Mono.error(new NotFoundException("Policy not found"));
                    }
                    return snapshotRepository.findByPolicyId(policyId)
                            .switchIfEmpty(Mono.error(
                                    new NotFoundException("Trigger snapshot not found for this policy")
                            ))
                            .map(this::toSnapshotResponse);
                });
    }

    private Mono<Void> recordHistory(
            Long policyId,
            String oldStatus,
            String newStatus,
            Long changedBy,
            String changeReason
    ){
        PolicyStatusHistory history=PolicyStatusHistory.builder()
                .policyId(policyId)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .changeReason(changeReason)
                .changedAt(LocalDateTime.now())
                .build();

        return historyRepository.save(history).then();
    }

    private boolean hasTriggerData(CreatePolicyRequest request){
        return request.getPerilCode()!=null&&!request.getPerilCode().isBlank();
    }

    private boolean hasCompleteTriggerConfiguration(Product product){
        return product.getPerilCode()!=null&&!product.getPerilCode().isBlank()
                &&product.getMeasurementType()!=null
                &&product.getThresholdValue()!=null
                &&product.getComparisonOperator()!=null;
    }

    private Mono<PolicyTriggerSnapshot> resolveSnapshot(
            Product product,
            Long policyId,
            CreatePolicyRequest request
    ){
        if(hasCompleteTriggerConfiguration(product)){
            return createSnapshot(product,policyId);
        }

        if(hasTriggerData(request)){
            return createSnapshotFromRequest(policyId,request);
        }

        return Mono.empty();
    }

    private Mono<PolicyTriggerSnapshot> createSnapshot(
            Product product,
            Long policyId
    ){
        PolicyTriggerSnapshot snapshot=PolicyTriggerSnapshot.builder()
                .policyId(policyId)
                .productId(product.getId())
                .productCode(product.getProductCode())
                .perilCode(product.getPerilCode())
                .measurementType(product.getMeasurementType())
                .measurementUnit(product.getMeasurementUnit())
                .thresholdValue(product.getThresholdValue())
                .comparisonOperator(product.getComparisonOperator())
                .aggregationType(product.getAggregationType())
                .observationWindowHours(product.getObservationWindowHours())
                .minimumObservationCount(product.getMinimumObservationCount())
                .triggerDescription(product.getTriggerDescription())
                .capturedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        return snapshotRepository.save(snapshot);
    }

    private Mono<PolicyTriggerSnapshot> createSnapshotFromRequest(
            Long policyId,
            CreatePolicyRequest request
    ){
        PolicyTriggerSnapshot snapshot=PolicyTriggerSnapshot.builder()
                .policyId(policyId)
                .perilCode(request.getPerilCode())
                .triggerMetric(request.getTriggerMetric())
                .triggerOperator(request.getTriggerOperator())
                .triggerThreshold(request.getTriggerThreshold())
                .triggerDurationHours(request.getTriggerDurationHours())
                .triggerAggregationType(request.getTriggerAggregationType())
                .triggerMinObservationCount(request.getTriggerMinObservationCount())
                .createdAt(LocalDateTime.now())
                .build();

        return snapshotRepository.save(snapshot);
    }

    private void validateCreateRequest(
            Long customerId,
            CreatePolicyRequest request
    ){
        validateCustomerId(customerId);

        if(request==null){
            throw new ValidationException(
                    "Policy request is required"
            );
        }

        if(request.getProductId()==null||
                request.getProductId()<=0){
            throw new ValidationException(
                    "Product ID must be greater than zero"
            );
        }

        if(request.getLocationZoneId()==null||
                request.getLocationZoneId()<=0){
            throw new ValidationException(
                    "Location zone ID must be greater than zero"
            );
        }

        if(request.getSumInsured()==null){
            throw new ValidationException(
                    "Sum insured is required"
            );
        }

        if(request.getSumInsured().signum()<=0){
            throw new ValidationException(
                    "Sum insured must be greater than zero"
            );
        }

        if(request.getTermMonths()==null){
            throw new ValidationException(
                    "Policy term is required"
            );
        }

        if(request.getTermMonths()<=0){
            throw new ValidationException(
                    "Policy term must be greater than zero"
            );
        }
    }

    private Mono<Void> validateQuoteIfPresent(
            Long customerId,
            CreatePolicyRequest request
    ){
        if(request.getQuoteId()==null){
            return Mono.empty();
        }

        InternalQuoteValidateRequest validateRequest=
                InternalQuoteValidateRequest.builder()
                        .customerId(customerId)
                        .productId(request.getProductId())
                        .locationZoneId(request.getLocationZoneId())
                        .sumInsured(request.getSumInsured())
                        .termMonths(request.getTermMonths())
                        .build();

        return financialServiceClient
                .validateQuote(request.getQuoteId(),validateRequest)
                .flatMap(response->{
                    if(!response.isValid()){
                        return Mono.error(new ValidationException(
                                response.getValidationReason()!=null
                                        ?response.getValidationReason()
                                        :"Quote is not valid for this application"
                        ));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> consumeQuoteIfPresent(
            CreatePolicyRequest request,
            Policy policy
    ){
        if(request.getQuoteId()==null){
            return Mono.empty();
        }

        InternalQuoteConsumeRequest consumeRequest=
                InternalQuoteConsumeRequest.builder()
                        .policyId(policy.getId())
                        .policyNumber(policy.getPolicyNumber())
                        .build();

        return financialServiceClient
                .consumeQuote(request.getQuoteId(),consumeRequest)
                .then();
    }

    private void validateSumInsured(
            Product product,
            BigDecimal sumInsured
    ){
        if(product.getMinimumSumInsured()==null||
                product.getMaximumSumInsured()==null){
            throw new ValidationException(
                    "Product sum insured limits are unavailable"
            );
        }

        if(sumInsured.compareTo(product.getMinimumSumInsured())<0){
            throw new ValidationException(
                    "Sum insured cannot be less than "+
                            product.getMinimumSumInsured()
            );
        }

        if(sumInsured.compareTo(product.getMaximumSumInsured())>0){
            throw new ValidationException(
                    "Sum insured cannot exceed "+
                            product.getMaximumSumInsured()
            );
        }
    }

    private void validatePolicyTerm(
            Product product,
            Integer termMonths
    ){
        if(product.getMinimumTermMonths()==null||
                product.getMaximumTermMonths()==null){
            throw new ValidationException(
                    "Product policy term limits are unavailable"
            );
        }

        if(termMonths<product.getMinimumTermMonths()){
            throw new ValidationException(
                    "Policy term cannot be less than "+
                            product.getMinimumTermMonths()+" months"
            );
        }

        if(termMonths>product.getMaximumTermMonths()){
            throw new ValidationException(
                    "Policy term cannot exceed "+
                            product.getMaximumTermMonths()+" months"
            );
        }
    }

    private void requireStatus(
            Policy policy,
            PolicyStatus expected,
            String message
    ){
        if(!expected.name().equals(policy.getStatus())){
            throw new ConflictException(message);
        }
    }

    private String validateStatus(String status){
        try{
            return PolicyStatus.valueOf(
                    status.trim().toUpperCase(Locale.ROOT)
            ).name();
        }catch(Exception exception){
            throw new ValidationException(
                    "Status must be PENDING, ACTIVE, REJECTED, CANCELLED or EXPIRED"
            );
        }
    }

    private static final BigDecimal BASE_ANNUAL_RATE=new BigDecimal("0.02");
    private static final BigDecimal MONTHS_PER_YEAR=BigDecimal.valueOf(12);

    private BigDecimal calculateTemporaryPremium(
            BigDecimal sumInsured,
            Integer termMonths
    ){
        BigDecimal termYears=BigDecimal.valueOf(termMonths)
                .divide(
                        MONTHS_PER_YEAR,
                        10,
                        RoundingMode.HALF_UP
                );

        return sumInsured
                .multiply(BASE_ANNUAL_RATE)
                .multiply(termYears)
                .setScale(2,RoundingMode.HALF_UP);
    }

    private String generatePolicyNumber(){
        String random=UUID.randomUUID()
                .toString()
                .replace("-","")
                .substring(0,8)
                .toUpperCase(Locale.ROOT);

        return "POL-"+Year.now().getValue()+"-"+random;
    }

    private void validateCustomerId(Long customerId){
        if(customerId==null||customerId<=0){
            throw new ValidationException(
                    "Customer ID must be greater than zero"
            );
        }
    }

    private void validateReviewerId(Long reviewerId){
        if(reviewerId==null||reviewerId<=0){
            throw new ValidationException(
                    "Reviewer ID must be greater than zero"
            );
        }
    }

    private void validatePolicyId(Long policyId){
        if(policyId==null||policyId<=0){
            throw new ValidationException(
                    "Policy ID must be greater than zero"
            );
        }
    }

    private PolicyResponse toResponse(Policy policy){
        return PolicyResponse.builder()
                .id(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .customerId(policy.getCustomerId())
                .productId(policy.getProductId())
                .productCode(policy.getProductCode())
                .productName(policy.getProductName())
                .locationZoneId(policy.getLocationZoneId())
                .sumInsured(policy.getSumInsured())
                .premiumAmount(policy.getPremiumAmount())
                .termMonths(policy.getTermMonths())
                .startDate(policy.getStartDate())
                .endDate(policy.getEndDate())
                .status(policy.getStatus())
                .rejectionReason(policy.getRejectionReason())
                .reviewedBy(policy.getReviewedBy())
                .reviewedAt(policy.getReviewedAt())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .triggerSnapshotId(policy.getTriggerSnapshotId())
                .build();
    }

    private PolicyStatusHistoryResponse toHistoryResponse(PolicyStatusHistory h){
        return PolicyStatusHistoryResponse.builder()
                .id(h.getId())
                .policyId(h.getPolicyId())
                .oldStatus(h.getOldStatus())
                .newStatus(h.getNewStatus())
                .changedBy(h.getChangedBy())
                .changeReason(h.getChangeReason())
                .changedAt(h.getChangedAt())
                .build();
    }

    private PolicyTriggerSnapshotResponse toSnapshotResponse(PolicyTriggerSnapshot s){
        return PolicyTriggerSnapshotResponse.builder()
                .id(s.getId())
                .policyId(s.getPolicyId())
                .productId(s.getProductId())
                .productCode(s.getProductCode())
                .perilCode(s.getPerilCode())
                .measurementType(s.getMeasurementType())
                .measurementUnit(s.getMeasurementUnit())
                .thresholdValue(s.getThresholdValue())
                .comparisonOperator(s.getComparisonOperator())
                .aggregationType(s.getAggregationType())
                .observationWindowHours(s.getObservationWindowHours())
                .minimumObservationCount(s.getMinimumObservationCount())
                .triggerDescription(s.getTriggerDescription())
                .humanReadableTrigger(TriggerDescriptionGenerator.toHumanReadable(
                        s.getMeasurementType(),
                        s.getMeasurementUnit(),
                        s.getThresholdValue(),
                        s.getComparisonOperator(),
                        s.getAggregationType(),
                        s.getObservationWindowHours()
                ))
                .triggerMetric(s.getTriggerMetric())
                .triggerOperator(s.getTriggerOperator())
                .triggerThreshold(s.getTriggerThreshold())
                .triggerDurationHours(s.getTriggerDurationHours())
                .triggerAggregationType(s.getTriggerAggregationType())
                .triggerMinObservationCount(s.getTriggerMinObservationCount())
                .capturedAt(s.getCapturedAt())
                .createdAt(s.getCreatedAt())
                .build();
    }

    public Mono<List<InternalZoneResponse>> getActiveZones(){
        return triggerServiceClient.getActiveZonesList()
                .onErrorResume(error -> {
                    throw new ValidationException("Unable to fetch location zones: " + error.getMessage());
                });
    }
}
