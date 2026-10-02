package org.example.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.productservice.client.TriggerServiceClient;
import org.example.productservice.client.ClaimsServiceClient;
import org.example.productservice.client.FinancialServiceClient;
import org.example.productservice.dto.*;
import org.example.productservice.exception.ConflictException;
import org.example.productservice.exception.NotFoundException;
import org.example.productservice.exception.ValidationException;
import org.example.productservice.model.*;
import org.example.productservice.repository.*;
import org.example.productservice.util.AuditDiff;
import org.example.productservice.util.TriggerDescriptionGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService{
    private static final int MAX_WINDOW_HOURS=720;

    private final ProductRepository productRepository;
    private final CoverageRepository coverageRepository;
    private final NotificationService notificationService;
    private final TriggerServiceClient triggerServiceClient;
    private final ClaimsServiceClient claimsServiceClient;
    private final FinancialServiceClient financialServiceClient;
    private final PolicyRepository policyRepository;
        private final PolicyTriggerSnapshotRepository snapshotRepository;
    private final ProductThresholdHistoryRepository thresholdHistoryRepository;

    @Transactional
    public Mono<ProductResponse> createProduct(
            Long adminId,
            ProductRequest request){
        log.info("Create product: adminId={}",adminId);

        validateProductValues(request);
        validateTriggerConfiguration(request);
        String code=normalizeCode(request.getProductCode());

        return productRepository.existsByProductCodeIgnoreCase(code)
                .flatMap(exists->{
                    if(exists){
                        return Mono.error(
                                new ConflictException(
                                        "Product code already exists"
                                )
                        );
                    }

                    LocalDateTime now=LocalDateTime.now();

                    Product product=Product.builder()
                            .productCode(code)
                            .name(request.getName().trim())
                            .description(trim(request.getDescription()))
                            .category(validateCategory(request.getCategory()))
                            .status(ProductStatus.DRAFT.name())
                            .minimumSumInsured(request.getMinimumSumInsured())
                            .maximumSumInsured(request.getMaximumSumInsured())
                            .minimumTermMonths(request.getMinimumTermMonths())
                            .maximumTermMonths(request.getMaximumTermMonths())
                            .eligibilityDescription(
                                    trim(request.getEligibilityDescription())
                            )
                            .perilCode(normalizeEnumValue(request.getPerilCode()))
                            .measurementType(
                                    normalizeEnumValue(request.getMeasurementType())
                            )
                            .measurementUnit(normalizeUnit(request.getMeasurementUnit()))
                            .thresholdValue(request.getThresholdValue())
                            .comparisonOperator(
                                    normalizeEnumValue(request.getComparisonOperator())
                            )
                            .aggregationType(
                                    normalizeEnumValue(request.getAggregationType())
                            )
                            .observationWindowHours(request.getObservationWindowHours())
                            .minimumObservationCount(request.getMinimumObservationCount())
                            .triggerDescription(resolveTriggerDescription(request))
                            .createdBy(adminId)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    return productRepository.save(product)
                            .flatMap(saved->
                                    saveCoverages(
                                            saved.getId(),
                                            request.getCoverages()
                                    ).then(Mono.just(saved))
                            )
                            .flatMap(saved->buildResponse(saved,false))
                            .flatMap(resp -> notificationService.notifyProductCreated(
                                    resp.getId(), resp.getName()
                            ).thenReturn(resp))
                            .flatMap(resp -> notificationService.audit(
                                    "PRODUCT_CREATED", "PRODUCT", resp.getId(), adminId,
                                    "Created product " + resp.getName() +
                                            " (" + resp.getProductCode() + ")"
                            ).thenReturn(resp));
                });
    }

    @Transactional
    public Mono<ProductResponse> createCustomerDefinedProduct(
            Long customerId,
            ProductRequest request){
        log.info("Create customer defined product: customerId={}",customerId);

        validateProductValues(request);
        validateTriggerConfiguration(request);
        String code=normalizeCode(request.getProductCode());

        return productRepository.existsByProductCodeIgnoreCase(code)
                .flatMap(exists->{
                    if(exists){
                        return Mono.error(
                                new ConflictException(
                                        "Product code already exists"
                                )
                        );
                    }

                    LocalDateTime now=LocalDateTime.now();

                    Product product=Product.builder()
                            .productCode(code)
                            .name(request.getName().trim())
                            .description(trim(request.getDescription()))
                            .category(validateCategory(request.getCategory()))
                            .status(ProductStatus.ACTIVE.name())
                            .minimumSumInsured(request.getMinimumSumInsured())
                            .maximumSumInsured(request.getMaximumSumInsured())
                            .minimumTermMonths(request.getMinimumTermMonths())
                            .maximumTermMonths(request.getMaximumTermMonths())
                            .eligibilityDescription(
                                    trim(request.getEligibilityDescription())
                            )
                            .perilCode(normalizeEnumValue(request.getPerilCode()))
                            .measurementType(
                                    normalizeEnumValue(request.getMeasurementType())
                            )
                            .measurementUnit(normalizeUnit(request.getMeasurementUnit()))
                            .thresholdValue(request.getThresholdValue())
                            .comparisonOperator(
                                    normalizeEnumValue(request.getComparisonOperator())
                            )
                            .aggregationType(
                                    normalizeEnumValue(request.getAggregationType())
                            )
                            .observationWindowHours(request.getObservationWindowHours())
                            .minimumObservationCount(request.getMinimumObservationCount())
                            .triggerDescription(resolveTriggerDescription(request))
                            .customerDefined(true)
                            .createdBy(customerId)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    return productRepository.save(product)
                            .flatMap(saved->syncDefinition(saved,customerId))
                            .flatMap(saved->buildResponse(saved,false));
                });
    }

    @Transactional
    public Mono<ProductResponse> updateProduct(
            Long productId,
            ProductRequest request){
        log.info("Update product: productId={}",productId);

        validateProductValues(request);
        String code=normalizeCode(request.getProductCode());

        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Product not found")
                ))
                .flatMap(product->
                        productRepository.findByProductCodeIgnoreCase(code)
                                .filter(existing->!existing.getId().equals(productId))
                                .hasElement()
                                .flatMap(duplicate->{
                                    if(duplicate){
                                        return Mono.error(
                                                new ConflictException(
                                                        "Product code already exists"
                                                )
                                        );
                                    }

                                    java.util.Map<String,Object> fieldsBefore=auditFields(product);

                                    product.setProductCode(code);
                                    product.setName(request.getName().trim());
                                    product.setDescription(
                                            trim(request.getDescription())
                                    );
                                    product.setCategory(
                                            validateCategory(request.getCategory())
                                    );
                                    product.setMinimumSumInsured(
                                            request.getMinimumSumInsured()
                                    );
                                    product.setMaximumSumInsured(
                                            request.getMaximumSumInsured()
                                    );
                                    product.setMinimumTermMonths(
                                            request.getMinimumTermMonths()
                                    );
                                    product.setMaximumTermMonths(
                                            request.getMaximumTermMonths()
                                    );
                                    product.setEligibilityDescription(
                                            trim(request.getEligibilityDescription())
                                    );

                                    TriggerSnapshotValues previous=
                                            snapshotOf(product);
                                    boolean triggerChanged=
                                            applyTriggerConfig(product,request);

                                    if(ProductStatus.ACTIVE.name().equals(
                                            product.getStatus())
                                    ){
                                        validateCompleteTrigger(product);
                                    }

                                    product.setUpdatedAt(LocalDateTime.now());

                                    return productRepository.save(product)
                                            .flatMap(saved->
                                                    coverageRepository
                                                            .deleteAllByProductId(productId)
                                                            .thenMany(saveCoverages(
                                                                    productId,
                                                                    request.getCoverages()
                                                            ))
                                                            .then(Mono.just(saved))
                                            )
                                            .flatMap(saved->triggerChanged
                                                    ?recordThresholdHistory(
                                                            saved,
                                                            previous,
                                                            null,
                                                            null
                                                    ).thenReturn(saved)
                                                    :Mono.just(saved))
                                            .flatMap(saved->
                                                    buildResponse(saved,false))
                                            .flatMap(resp -> notificationService.notifyProductUpdated(
                                                    resp.getId(), resp.getName()
                                            ).thenReturn(resp))
                                            .flatMap(resp -> {
                                                AuditDiff diff=AuditDiff.of(fieldsBefore,auditFields(product));
                                                return notificationService.auditChange(
                                                        "PRODUCT_UPDATED", "PRODUCT", resp.getId(), null,
                                                        "Updated product " + resp.getName() +
                                                                " (" + resp.getProductCode() + ")",
                                                        diff.before(), diff.after()
                                                ).thenReturn(resp);
                                            });
                                })
                );
    }

    public Flux<ProductResponse> getActiveProducts(){
        log.debug("Get active products");
        return productRepository.findAllByStatus(ProductStatus.ACTIVE.name())
                .filter(product->!Boolean.TRUE.equals(product.getCustomerDefined()))
                .concatMap(product->buildResponse(product,true));
    }

    public Mono<ProductResponse> getActiveProduct(Long productId){
        log.debug("Get active product: productId={}",productId);
        return productRepository.findByIdAndStatus(productId,ProductStatus.ACTIVE.name())
                .switchIfEmpty(Mono.error(new NotFoundException("Active product not found")))
                .flatMap(product->buildResponse(product,true));
    }

    public Flux<ProductResponse> getProductsByCategory(String category){
        log.debug("Get products by category: category={}",category);
        String validatedCategory=validateCategory(category);

        return productRepository.findAllByCategoryAndStatus(
                validatedCategory,
                ProductStatus.ACTIVE.name()
        )
                .filter(product->!Boolean.TRUE.equals(product.getCustomerDefined()))
                .concatMap(product->buildResponse(product,true));
    }

    public Flux<ProductResponse> getAllProducts(){
        log.debug("Get all products");
        return productRepository.findAll()
                .concatMap(product->buildResponse(product,false));
    }

    public Flux<EligiblePolicyResponse> getEligiblePoliciesForEvent(
            Long productId,
            Long locationZoneId,
            LocalDateTime eventStart,
            LocalDateTime eventEnd,
            Long triggerEventId){
        log.debug("Get eligible policies for event [product]: productId={}, locationZoneId={}, triggerEventId={}",productId,locationZoneId,triggerEventId);
        return policyRepository.findAllByProductIdAndStatus(productId,"ACTIVE")
                .filter(policy->locationZoneId.equals(policy.getLocationZoneId())
                        && policy.getStartDate()!=null
                        && policy.getEndDate()!=null
                        && !policy.getStartDate().isAfter(eventEnd.toLocalDate())
                        && !policy.getEndDate().isBefore(eventStart.toLocalDate()))
                .flatMap(policy->Mono.zip(
                                snapshotRepository.findByPolicyId(policy.getId())
                                        .defaultIfEmpty(new PolicyTriggerSnapshot()),
                                financialServiceClient.isPremiumPaid(policy.getId()))
                        .map(tuple->{
                                PolicyTriggerSnapshot snapshot=tuple.getT1();
                                return EligiblePolicyResponse.builder()
                                .policyId(policy.getId())
                                .policyNumber(policy.getPolicyNumber())
                                .customerId(policy.getCustomerId())
                                .productId(policy.getProductId())
                                .productCode(policy.getProductCode())
                                .productName(policy.getProductName())
                                .locationZoneId(policy.getLocationZoneId())
                                .sumInsured(policy.getSumInsured())
                                .startDate(policy.getStartDate().atStartOfDay())
                                .endDate(policy.getEndDate().plusDays(1).atStartOfDay())
                                .status(policy.getStatus())
                                .premiumPaid(tuple.getT2())
                                .triggerSnapshot(EligiblePolicyResponse.TriggerSnapshotResponse.builder()
                                        .measurementType(snapshot.getMeasurementType())
                                        .measurementUnit(snapshot.getMeasurementUnit())
                                        .aggregationType(snapshot.getAggregationType())
                                        .comparisonOperator(snapshot.getComparisonOperator())
                                        .thresholdValue(snapshot.getThresholdValue())
                                        .observationWindowHours(snapshot.getObservationWindowHours())
                                        .minimumObservationCount(snapshot.getMinimumObservationCount())
                                        .build())
                                .build();
                        }));
    }

    public Mono<Boolean> hasActivePoliciesForZone(Long locationZoneId){
        log.debug("Has active policies for zone [product]: locationZoneId={}",locationZoneId);
        return Flux.merge(
                        policyRepository.findAllByLocationZoneIdAndStatus(locationZoneId, PolicyStatus.PENDING.name()),
                        policyRepository.findAllByLocationZoneIdAndStatus(locationZoneId, PolicyStatus.ACTIVE.name())
                )
                .hasElements();
    }

    @Transactional
    public Mono<ProductResponse> updateStatus(
            Long productId,
            UpdateProductStatusRequest request){
        log.info("Update status [product]: productId={}",productId);

        String status=validateStatus(request.getStatus());

        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new NotFoundException("Product not found")))
                .flatMap(product->{
                    if(ProductStatus.ACTIVE.name().equals(status)){
                        validateCompleteTrigger(product);
                    }

                    // Archiving from the status dropdown follows the same rule as the Archive action.
                    Mono<Void> archiveCheck=ProductStatus.ARCHIVED.name().equals(status)
                            ?requireNoOpenPolicies(productId)
                            :Mono.empty();

                    String oldStatus=product.getStatus();
                    return archiveCheck.then(Mono.defer(()->{
                        product.setStatus(status);
                        product.setUpdatedAt(LocalDateTime.now());
                        return productRepository.save(product);
                    }))
                    .flatMap(saved->notificationService.auditChange(
                            "PRODUCT_STATUS_CHANGED", "PRODUCT", saved.getId(), null,
                            "Changed status of product " + saved.getProductCode() +
                                    " to " + status,
                            "Status: " + oldStatus,
                            "Status: " + status
                    ).thenReturn(saved));
                })
                .flatMap(product->buildResponse(product,false));
    }

    private Mono<Void> requireNoOpenPolicies(Long productId){
        return Flux.merge(
                        policyRepository.findAllByProductIdAndStatus(productId, PolicyStatus.PENDING.name()),
                        policyRepository.findAllByProductIdAndStatus(productId, PolicyStatus.ACTIVE.name())
                )
                .hasElements()
                .flatMap(open->open
                        ?Mono.error(new ConflictException(
                                "Cannot archive product: it has pending or active policies referencing it"))
                        :Mono.empty());
    }

    @Transactional
    public Mono<MessageResponse> deleteProduct(Long productId){
        log.info("Delete product: productId={}",productId);
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new NotFoundException("Product not found")))
                .flatMap(product->
                        Flux.merge(
                                        policyRepository.findAllByProductIdAndStatus(productId, PolicyStatus.PENDING.name()),
                                        policyRepository.findAllByProductIdAndStatus(productId, PolicyStatus.ACTIVE.name())
                                )
                                .hasElements()
                                .flatMap(hasActivePolicies->{
                                    if(Boolean.TRUE.equals(hasActivePolicies)){
                                        return Mono.error(new ConflictException(
                                                "Cannot archive product: it has pending or active policies referencing it"
                                        ));
                                    }

                                    product.setStatus(ProductStatus.ARCHIVED.name());
                                    product.setUpdatedAt(LocalDateTime.now());
                                    return productRepository.save(product);
                                })
                )
                .flatMap(product->notificationService.audit(
                        "PRODUCT_ARCHIVED", "PRODUCT", product.getId(), null,
                        "Archived product " + product.getProductCode()
                ).thenReturn(product))
                .thenReturn(MessageResponse.builder()
                        .message("Product archived successfully")
                        .build());
    }

@Transactional
    public Mono<ProductThresholdUpdateResponse> updateThreshold(
            Long adminId,
            Long productId,
            ProductThresholdUpdateRequest request){
        log.info("Update threshold [product]: adminId={}, productId={}",adminId,productId);

        if(productId==null||productId<=0){
            return Mono.error(new ValidationException(
                    "Product id must be greater than zero"
            ));
        }

        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new NotFoundException("Product not found")))
                .flatMap(product->{
                    validateTriggerCompatibility(
                            request.getPerilCode(),
                            request.getMeasurementType(),
                            request.getMeasurementUnit()
                    );

                    TriggerSnapshotValues previous=snapshotOf(product);

                    applyThresholdRequest(product,request);

                    return recordThresholdHistory(
                                    product,
                                    previous,
                                    adminId,
                                    request.getChangeReason()
                            )
                            .then(productRepository.save(product))
                            .flatMap(saved->{
                                AuditDiff diff=AuditDiff.of(
                                        triggerAuditFields(previous),
                                        triggerAuditFields(snapshotOf(saved)));
                                return notificationService.auditChange(
                                        "PRODUCT_THRESHOLD_UPDATED", "PRODUCT", saved.getId(), adminId,
                                        "Changed trigger threshold of product " + saved.getProductCode() +
                                                " from " + previous.thresholdValue() +
                                                " to " + saved.getThresholdValue(),
                                        diff.before(), diff.after()
                                ).thenReturn(saved);
                            })
                            .map(saved->ProductThresholdUpdateResponse.builder()
                                    .productId(saved.getId())
                                    .productCode(saved.getProductCode())
                                    .previousThresholdValue(previous.thresholdValue())
                                    .newThresholdValue(saved.getThresholdValue())
                                    .measurementUnit(saved.getMeasurementUnit())
                                    .triggerDescription(saved.getTriggerDescription())
                                    .triggerSyncStatus(saved.getTriggerSyncStatus())
                                    .existingPoliciesUpdated(false)
                                    .message("Threshold updated and trigger definitions synchronized. Existing policies retain their captured trigger snapshot and are unaffected.")
                                    .build());
                });
    }

    private Mono<Long> fetchAndCreateClaimsForEligiblePolicies(Long productId){
        return policyRepository.findAllByProductIdAndStatus(productId,"ACTIVE")
                .flatMap(policy->
                        triggerServiceClient.getPolicyEligibility(policy.getId())
                                .flatMap(eligibility->{
                                    if(eligibility==null||
                                            !eligibility.isEligible()||
                                            eligibility.getTriggerEventId()==null){
                                        return Mono.just(0L);
                                    }

                                    InternalClaimRequest claimReq=
                                            InternalClaimRequest.builder()
                                                    .policyId(policy.getId())
                                                    .incidentType("PARAMETRIC_EVENT")
                                                    .incidentDate(
                                                            java.time.LocalDate.now())
                                                    .incidentLocation("AUTO")
                                                    .description("Automatic claim created from a verified trigger event")
                                                    .claimedAmount(eligibility.getAggregatedValue()!=null
                                                            ?eligibility.getAggregatedValue()
                                                            :java.math.BigDecimal.ZERO)
                                                    .triggerEventId(
                                                            eligibility.getTriggerEventId())
                                                    .claimSource("AUTO_TRIGGERED")
                                                    .build();

                                    return claimsServiceClient.createInternalClaim(claimReq)
                                            .thenReturn(1L);
                                })
                                .onErrorResume(error->{
                                    log.warn("Eligibility or claim creation failed for policy {}: {}",
                                            policy.getId(),error.getMessage());
                                    return Mono.just(0L);
                                }))
                .reduce(0L,Long::sum);
    }

    public Mono<VerifiedEventProcessingResponse> processVerifiedEvent(
            VerifiedEventRequest request){
        log.info("Process verified event [product]");
        if(request==null||
                request.getProductId()==null||
                request.getEventId()==null){
            return Mono.error(new ValidationException(
                    "productId and eventId are required"
            ));
        }

        return productRepository.findById(request.getProductId())
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Product not found for verified trigger event"
                )))
                .flatMap(product->fetchAndCreateClaimsForEligiblePolicies(
                                product.getId())
                        .map(created->VerifiedEventProcessingResponse.builder()
                                .productId(product.getId())
                                .eventId(request.getEventId())
                                .claimsCreated(created)
                                .message(created>0
                                        ?created+" automatic claim(s) created for eligible policies"
                                        :"No eligible policies matched the verified event")
                                .build()));
    }

    public Flux<MeasurementStatusResponse> getMeasurementStatus(){
        log.debug("Get measurement status [product]");
        return getMeasurementStatus(productRepository.findAll());
    }

    public Flux<MeasurementStatusResponse> getPublicMeasurementStatus(){
        log.debug("Get public measurement status [product]");
        return getMeasurementStatus(
                productRepository.findAllByStatus(ProductStatus.ACTIVE.name())
        );
    }

    private Flux<MeasurementStatusResponse> getMeasurementStatus(
            Flux<Product> productSource){
        return triggerServiceClient.getCurrentMeasurements()
                .collectMap(
                        current->normalizeMeasurementTypeKey(
                                current.getMeasurementType()),
                        current->current)
                .flatMapMany(byType->productSource
                        .filter(this::hasTriggerConfiguration)
                        .filter(product->hasDashboardType(product))
                        .groupBy(product->normalizeMeasurementTypeKey(
                                product.getMeasurementType()))
                        .flatMap(group->group.collectList()
                                .map(products->buildMeasurementStatus(
                                        byType.get(group.key()),
                                        products)))
                        .sort(
                                (left,right)->String.CASE_INSENSITIVE_ORDER
                                        .compare(
                                                left.getMeasurementType(),
                                                right.getMeasurementType())
                        ));
    }

    private MeasurementStatusResponse buildMeasurementStatus(
            MeasurementCurrentResponse current,
            List<Product> products){
        if(products==null||products.isEmpty()){
            return MeasurementStatusResponse.builder()
                    .measurementType(null)
                    .displayName(null)
                    .products(List.of())
                    .build();
        }

        String typeKey=normalizeMeasurementTypeKey(
                products.get(0).getMeasurementType());

        List<ProductTriggerStatus> statuses=products.stream()
                .map(product->ProductTriggerStatus.builder()
                        .productId(product.getId())
                        .productCode(product.getProductCode())
                        .productName(product.getName())
                        .thresholdValue(product.getThresholdValue())
                        .comparisonOperator(product.getComparisonOperator())
                        .aggregationType(product.getAggregationType())
                        .observationWindowHours(product.getObservationWindowHours())
                        .minimumObservationCount(product.getMinimumObservationCount())
                        .triggered(current!=null&&isTriggered(
                                current.getCurrentValue(),product))
                        .build())
                .toList();

        return MeasurementStatusResponse.builder()
                .measurementType(typeKey)
                .displayName(displayLabel(typeKey))
                .measurementUnit(current!=null
                        ?current.getMeasurementUnit()
                        :products.get(0).getMeasurementUnit())
                .currentValue(current!=null
                        ?current.getCurrentValue():null)
                .observedAt(current!=null
                        ?current.getObservedAt():null)
                .zoneCode(current!=null
                        ?current.getZoneCode():null)
                .zoneName(current!=null
                        ?current.getZoneName():null)
                .source(current!=null
                        ?current.getSource():null)
                .products(statuses)
                .build();
    }

    private boolean isTriggered(
            java.math.BigDecimal currentValue,
            Product product){
        if(currentValue==null||
                product.getThresholdValue()==null||
                !StringUtils.hasText(product.getComparisonOperator())){
            return false;
        }

        ComparisonOperator operator;
        try{
            operator=ComparisonOperator.valueOf(
                    product.getComparisonOperator()
                            .trim().toUpperCase(Locale.ROOT));
        }catch(IllegalArgumentException exception){
            return false;
        }

        int comparison=currentValue.compareTo(product.getThresholdValue());

        return switch(operator){
            case GREATER_THAN->comparison>0;
            case GREATER_THAN_OR_EQUAL->comparison>=0;
            case LESS_THAN->comparison<0;
            case LESS_THAN_OR_EQUAL->comparison<=0;
            case EQUAL->comparison==0;
        };
    }

    private boolean hasDashboardType(Product product){
        String type=normalizeMeasurementTypeKey(
                product.getMeasurementType());
        return type!=null&&!"OTHER".equals(type);
    }

    private String normalizeMeasurementTypeKey(String measurementType){
        if(!StringUtils.hasText(measurementType)){
            return null;
        }
        String normalized=measurementType.trim()
                .toUpperCase(Locale.ROOT);
        if("WATER_LEVEL".equals(normalized)){
            return "RIVER_LEVEL";
        }
        return normalized;
    }

    private String displayLabel(String measurementType){
        if(!StringUtils.hasText(measurementType)){
            return null;
        }
        return switch(measurementType){
            case "RAINFALL"->"Rainfall";
            case "RIVER_LEVEL"->"River Level";
            case "TEMPERATURE"->"Temperature";
            case "WIND_SPEED"->"Wind Speed";
            case "HUMIDITY"->"Humidity";
            case "SOIL_MOISTURE"->"Soil Moisture";
            default->measurementType;
        };
    }

    public Flux<ProductThresholdHistoryResponse> getThresholdHistory(Long productId){
        log.debug("Get threshold history [product]: productId={}",productId);
        if(productId==null||productId<=0){
            return Flux.error(new ValidationException(
                    "Product id must be greater than zero"
            ));
        }

        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new NotFoundException("Product not found")))
                .thenMany(thresholdHistoryRepository
                        .findAllByProductIdOrderByChangedAtDesc(productId))
                .map(this::toHistoryResponse);
    }

    @Transactional
    public Mono<ProductTriggerSyncResponse> syncTrigger(Long adminId,Long productId){
        log.info("Sync trigger [product]: adminId={}, productId={}",adminId,productId);
        if(productId==null||productId<=0){
            return Mono.error(new ValidationException(
                    "Product id must be greater than zero"
            ));
        }

        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new NotFoundException("Product not found")))
                .flatMap(product->{
                    validateCompleteTrigger(product);
                    return syncDefinition(product,adminId);
                })
                .map(saved->ProductTriggerSyncResponse.builder()
                        .productId(saved.getId())
                        .productCode(saved.getProductCode())
                        .syncStatus(saved.getTriggerSyncStatus())
                        .syncedAt(saved.getTriggerSyncedAt())
                        .message(synchronizationMessage(saved))
                        .build());
    }

    private String synchronizationMessage(Product product){
        if(TriggerSyncStatus.SYNCED.name().equals(product.getTriggerSyncStatus())){
            return "Trigger configuration synchronized successfully";
        }

        return "Trigger synchronization failed: "+
                (product.getTriggerSyncError()!=null
                        ?product.getTriggerSyncError()
                        :"unknown error");
    }

    private Mono<Void> recordThresholdHistory(
            Product product,
            TriggerSnapshotValues previous,
            Long changedBy,
            String changeReason
    ){
        TriggerSnapshotValues current=snapshotOf(product);

        if(previous.equals(current)){
            return Mono.empty();
        }

        ProductThresholdHistory history=ProductThresholdHistory.builder()
                .productId(product.getId())
                .productCode(product.getProductCode())
                .previousPerilCode(previous.perilCode())
                .newPerilCode(current.perilCode())
                .previousMeasurementType(previous.measurementType())
                .newMeasurementType(current.measurementType())
                .previousMeasurementUnit(previous.measurementUnit())
                .newMeasurementUnit(current.measurementUnit())
                .previousThresholdValue(previous.thresholdValue())
                .newThresholdValue(current.thresholdValue())
                .previousComparisonOperator(previous.comparisonOperator())
                .newComparisonOperator(current.comparisonOperator())
                .previousAggregationType(previous.aggregationType())
                .newAggregationType(current.aggregationType())
                .previousObservationWindowHours(previous.observationWindowHours())
                .newObservationWindowHours(current.observationWindowHours())
                .previousMinimumObservationCount(previous.minimumObservationCount())
                .newMinimumObservationCount(current.minimumObservationCount())
                .changedBy(changedBy)
                .changeReason(trim(changeReason))
                .changedAt(LocalDateTime.now())
                .triggerSyncStatus(product.getTriggerSyncStatus())
                .build();

        return thresholdHistoryRepository.save(history).then();
    }

    private Mono<Product> syncDefinition(Product product,Long userId){
        if(!hasTriggerConfiguration(product)){
            return Mono.just(product);
        }

        ProductTriggerSyncRequest request=ProductTriggerSyncRequest.builder()
                .productId(product.getId())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .productStatus(product.getStatus())
                .perilCode(product.getPerilCode())
                .measurementType(product.getMeasurementType())
                .measurementUnit(product.getMeasurementUnit())
                .thresholdValue(product.getThresholdValue())
                .comparisonOperator(product.getComparisonOperator())
                .aggregationType(product.getAggregationType())
                .observationWindowHours(product.getObservationWindowHours())
                .minimumObservationCount(product.getMinimumObservationCount())
                .triggerDescription(product.getTriggerDescription())
                .updatedBy(userId)
                .build();

        return triggerServiceClient.syncProductDefinition(request)
                .then(markTriggerSync(product,TriggerSyncStatus.SYNCED,null))
                .onErrorResume(error->markTriggerSync(
                        product,
                        TriggerSyncStatus.FAILED,
                        error.getMessage()
                ));
    }

    private Mono<Product> markTriggerSync(
            Product product,
            TriggerSyncStatus status,
            String error){
        product.setTriggerSyncStatus(status.name());
        product.setTriggerSyncedAt(LocalDateTime.now());
        product.setTriggerSyncError(truncate(error,1000));
        return productRepository.save(product);
    }

    private Mono<ProductResponse> buildResponse(Product product,boolean activeOnly){
        Flux<Coverage> coverages=activeOnly
                ?coverageRepository.findAllByProductIdAndActiveTrue(product.getId())
                :coverageRepository.findAllByProductId(product.getId());

        return coverages.map(this::toCoverageResponse)
                .collectList()
                .map(items->ProductResponse.builder()
                        .id(product.getId())
                        .productCode(product.getProductCode())
                        .name(product.getName())
                        .description(product.getDescription())
                        .category(product.getCategory())
                        .status(product.getStatus())
                        .minimumSumInsured(product.getMinimumSumInsured())
                        .maximumSumInsured(product.getMaximumSumInsured())
                        .minimumTermMonths(product.getMinimumTermMonths())
                        .maximumTermMonths(product.getMaximumTermMonths())
                        .eligibilityDescription(product.getEligibilityDescription())
                        .perilCode(product.getPerilCode())
                        .measurementType(product.getMeasurementType())
                        .measurementUnit(product.getMeasurementUnit())
                        .thresholdValue(product.getThresholdValue())
                        .comparisonOperator(product.getComparisonOperator())
                        .aggregationType(product.getAggregationType())
                        .observationWindowHours(product.getObservationWindowHours())
                        .minimumObservationCount(product.getMinimumObservationCount())
                        .triggerDescription(product.getTriggerDescription())
                        .humanReadableTrigger(
                                TriggerDescriptionGenerator.toHumanReadable(
                                        product.getMeasurementType(),
                                        product.getMeasurementUnit(),
                                        product.getThresholdValue(),
                                        product.getComparisonOperator(),
                                        product.getAggregationType(),
                                        product.getObservationWindowHours()
                                )
                        )
                        .triggerSyncStatus(product.getTriggerSyncStatus())
                        .triggerSyncedAt(product.getTriggerSyncedAt())
                        .triggerSyncError(product.getTriggerSyncError())
                        .customerDefined(Boolean.TRUE.equals(product.getCustomerDefined()))
                        .createdBy(product.getCreatedBy())
                        .coverages(items)
                        .createdAt(product.getCreatedAt())
                        .updatedAt(product.getUpdatedAt())
                        .build());
    }

    private CoverageResponse toCoverageResponse(Coverage coverage){
        return CoverageResponse.builder()
                .id(coverage.getId())
                .coverageCode(coverage.getCoverageCode())
                .name(coverage.getName())
                .description(coverage.getDescription())
                .coverageLimit(coverage.getCoverageLimit())
                .mandatory(coverage.getMandatory())
                .active(coverage.getActive())
                .createdAt(coverage.getCreatedAt())
                .updatedAt(coverage.getUpdatedAt())
                .build();
    }

    private boolean applyTriggerConfig(Product product,ProductRequest request){
        TriggerSnapshotValues previous=snapshotOf(product);

        if(StringUtils.hasText(request.getPerilCode())){
            product.setPerilCode(normalizeEnumValue(request.getPerilCode()));
        }

        if(StringUtils.hasText(request.getMeasurementType())){
            product.setMeasurementType(
                    normalizeEnumValue(request.getMeasurementType())
            );
        }

        if(StringUtils.hasText(request.getMeasurementUnit())){
            product.setMeasurementUnit(normalizeUnit(request.getMeasurementUnit()));
        }

        if(request.getThresholdValue()!=null){
            product.setThresholdValue(request.getThresholdValue());
        }

        if(StringUtils.hasText(request.getComparisonOperator())){
            product.setComparisonOperator(
                    normalizeEnumValue(request.getComparisonOperator())
            );
        }

        if(StringUtils.hasText(request.getAggregationType())){
            product.setAggregationType(
                    normalizeEnumValue(request.getAggregationType())
            );
        }

        if(request.getObservationWindowHours()!=null){
            product.setObservationWindowHours(
                    request.getObservationWindowHours()
            );
        }

        if(request.getMinimumObservationCount()!=null){
            product.setMinimumObservationCount(
                    request.getMinimumObservationCount()
            );
        }

        validateTriggerCompatibility(
                product.getPerilCode(),
                product.getMeasurementType(),
                product.getMeasurementUnit()
        );

        if(hasTriggerConfiguration(product)&&
                !StringUtils.hasText(product.getTriggerDescription())){
            product.setTriggerDescription(buildDescription(product));
        }else if(StringUtils.hasText(request.getTriggerDescription())){
            product.setTriggerDescription(request.getTriggerDescription().trim());
        }

        return !previous.equals(snapshotOf(product));
    }

    private void applyThresholdRequest(
            Product product,
            ProductThresholdUpdateRequest request){
        product.setPerilCode(normalizeEnumValue(request.getPerilCode()));
        product.setMeasurementType(
                normalizeEnumValue(request.getMeasurementType())
        );
        product.setMeasurementUnit(normalizeUnit(request.getMeasurementUnit()));
        product.setThresholdValue(request.getThresholdValue());
        product.setComparisonOperator(
                normalizeEnumValue(request.getComparisonOperator())
        );
        product.setAggregationType(
                normalizeEnumValue(request.getAggregationType())
        );
        product.setObservationWindowHours(request.getObservationWindowHours());
        product.setMinimumObservationCount(request.getMinimumObservationCount());
        product.setTriggerDescription(
                StringUtils.hasText(request.getTriggerDescription())
                        ?request.getTriggerDescription().trim()
                        :buildDescription(product)
        );
    }

    private String buildDescription(Product product){
        return TriggerDescriptionGenerator.buildDescription(
                product.getMeasurementType(),
                product.getMeasurementUnit(),
                product.getThresholdValue(),
                product.getComparisonOperator(),
                product.getAggregationType(),
                product.getObservationWindowHours(),
                product.getMinimumObservationCount()
        );
    }

    private String resolveTriggerDescription(ProductRequest request){
        if(StringUtils.hasText(request.getTriggerDescription())){
            return request.getTriggerDescription().trim();
        }

        return TriggerDescriptionGenerator.buildDescription(
                normalizeEnumValue(request.getMeasurementType()),
                normalizeUnit(request.getMeasurementUnit()),
                request.getThresholdValue(),
                normalizeEnumValue(request.getComparisonOperator()),
                normalizeEnumValue(request.getAggregationType()),
                request.getObservationWindowHours(),
                request.getMinimumObservationCount()
        );
    }

    private void validateTriggerConfiguration(ProductRequest request){
        if(!StringUtils.hasText(request.getPerilCode())){
            throw new ValidationException(
                    "perilCode is required for a parametric product"
            );
        }

        if(!StringUtils.hasText(request.getMeasurementType())){
            throw new ValidationException(
                    "measurementType is required for a parametric product"
            );
        }

        if(!StringUtils.hasText(request.getMeasurementUnit())){
            throw new ValidationException(
                    "measurementUnit is required for a parametric product"
            );
        }

        if(request.getThresholdValue()==null){
            throw new ValidationException(
                    "thresholdValue is required for an active parametric product"
            );
        }

        if(request.getThresholdValue().signum()<=0){
            throw new ValidationException(
                    "thresholdValue must be greater than zero"
            );
        }

        validateWindowAndCount(
                request.getObservationWindowHours(),
                request.getMinimumObservationCount()
        );

        validateTriggerCompatibility(
                request.getPerilCode(),
                request.getMeasurementType(),
                request.getMeasurementUnit()
        );

        parseEnumValue(
                request.getComparisonOperator(),
                ComparisonOperator.class,
                "comparison operator"
        );
        parseEnumValue(
                request.getAggregationType(),
                AggregationType.class,
                "aggregation type"
        );
    }

    private void validateWindowAndCount(
            Integer windowHours,
            Integer minimumObservationCount){
        if(windowHours==null){
            throw new ValidationException(
                    "observationWindowHours is required"
            );
        }

        if(windowHours<1){
            throw new ValidationException(
                    "observationWindowHours must be greater than zero"
            );
        }

        if(windowHours>MAX_WINDOW_HOURS){
            throw new ValidationException(
                    "observationWindowHours cannot exceed "+MAX_WINDOW_HOURS+" hours"
            );
        }

        if(minimumObservationCount==null){
            throw new ValidationException(
                    "minimumObservationCount is required"
            );
        }

        if(minimumObservationCount<1){
            throw new ValidationException(
                    "minimumObservationCount must be at least 1"
            );
        }

        if(minimumObservationCount>windowHours){
            throw new ValidationException(
                    "minimumObservationCount cannot exceed the observation window hours"
            );
        }
    }

    private void validateTriggerCompatibility(
            String perilCode,
            String measurementType,
            String measurementUnit){
        MeasurementType type=parseEnumValue(
                measurementType,
                MeasurementType.class,
                "measurement type"
        );

        MeasurementUnit unit=MeasurementUnit.fromText(measurementUnit);
        if(unit==null){
            throw new ValidationException(
                    "Invalid measurement unit: "+
                            (measurementUnit==null?"":measurementUnit)
            );
        }

        boolean unitCompatible=switch(type){
            case RAINFALL->unit==MeasurementUnit.MM;
            case WATER_LEVEL->unit==MeasurementUnit.METER;
            case TEMPERATURE->unit==MeasurementUnit.CELSIUS;
            case WIND_SPEED->unit==MeasurementUnit.KM_PER_HOUR;
            case HUMIDITY,SOIL_MOISTURE->unit==MeasurementUnit.PERCENTAGE;
            default->true;
        };

        if(!unitCompatible){
            throw new ValidationException(
                    type.name()+" measurement must use "+
                            expectedUnit(type).getDisplay()+
                            " as its unit"
            );
        }

        PerilType peril=parseEnumValue(
                perilCode,
                PerilType.class,
                "peril code"
        );

        MeasurementType expectedForPeril=expectedMeasurement(peril);
        if(expectedForPeril!=null&&expectedForPeril!=type){
            throw new ValidationException(
                    peril.name()+" peril must use "+expectedForPeril.name()+
                            " measurement type"
            );
        }
    }

    private MeasurementUnit expectedUnit(MeasurementType type){
        return switch(type){
            case RAINFALL->MeasurementUnit.MM;
            case WATER_LEVEL->MeasurementUnit.METER;
            case TEMPERATURE->MeasurementUnit.CELSIUS;
            case WIND_SPEED->MeasurementUnit.KM_PER_HOUR;
            case HUMIDITY,SOIL_MOISTURE->MeasurementUnit.PERCENTAGE;
            default->null;
        };
    }

    private MeasurementType expectedMeasurement(PerilType peril){
        return switch(peril){
            case EXCESS_RAINFALL->MeasurementType.RAINFALL;
            case DROUGHT->MeasurementType.SOIL_MOISTURE;
            case FLOOD->MeasurementType.WATER_LEVEL;
            case EXTREME_TEMPERATURE->MeasurementType.TEMPERATURE;
            case HIGH_WIND,CYCLONE->MeasurementType.WIND_SPEED;
            case OTHER->null;
        };
    }

    private void validateCompleteTrigger(Product product){
        if(!hasTriggerConfiguration(product)){
            throw new ValidationException(
                    "Products cannot be activated without a complete threshold configuration"
            );
        }
    }

    private boolean hasTriggerConfiguration(Product product){
        return product!=null&&
                StringUtils.hasText(product.getPerilCode())&&
                StringUtils.hasText(product.getMeasurementType())&&
                StringUtils.hasText(product.getMeasurementUnit())&&
                product.getThresholdValue()!=null&&
                StringUtils.hasText(product.getComparisonOperator())&&
                StringUtils.hasText(product.getAggregationType())&&
                product.getObservationWindowHours()!=null&&
                product.getMinimumObservationCount()!=null;
    }

    /** Product fields shown in the audit log as before/after values. */
    private java.util.Map<String,Object> auditFields(Product product){
        java.util.Map<String,Object> fields=new java.util.LinkedHashMap<>();
        fields.put("Code",product.getProductCode());
        fields.put("Name",product.getName());
        fields.put("Category",product.getCategory());
        fields.put("Min sum insured",product.getMinimumSumInsured());
        fields.put("Max sum insured",product.getMaximumSumInsured());
        fields.put("Min term (months)",product.getMinimumTermMonths());
        fields.put("Max term (months)",product.getMaximumTermMonths());
        fields.putAll(triggerAuditFields(snapshotOf(product)));
        return fields;
    }

    private java.util.Map<String,Object> triggerAuditFields(TriggerSnapshotValues values){
        String unit=values.measurementUnit()==null?"":" "+values.measurementUnit();
        java.util.Map<String,Object> fields=new java.util.LinkedHashMap<>();
        fields.put("Peril",values.perilCode());
        fields.put("Measurement",values.measurementType());
        fields.put("Threshold",values.thresholdValue()==null
                ?null
                :values.thresholdValue().stripTrailingZeros().toPlainString()+unit);
        fields.put("Operator",values.comparisonOperator());
        fields.put("Aggregation",values.aggregationType());
        fields.put("Window (hours)",values.observationWindowHours());
        fields.put("Min observations",values.minimumObservationCount());
        return fields;
    }

    private TriggerSnapshotValues snapshotOf(Product product){
        return new TriggerSnapshotValues(
                product.getPerilCode(),
                product.getMeasurementType(),
                product.getMeasurementUnit(),
                product.getThresholdValue(),
                product.getComparisonOperator(),
                product.getAggregationType(),
                product.getObservationWindowHours(),
                product.getMinimumObservationCount(),
                product.getTriggerDescription()
        );
    }

    private String normalizeEnumValue(String value){
        return StringUtils.hasText(value)
                ?value.trim().toUpperCase(Locale.ROOT)
                :value;
    }

    private String normalizeUnit(String value){
        if(!StringUtils.hasText(value)){
            return value;
        }

        MeasurementUnit unit=MeasurementUnit.fromText(value);
        return unit!=null?unit.getDisplay():value.trim();
    }

    private <T extends Enum<T>> T parseEnumValue(
            String value,
            Class<T> enumClass,
            String fieldName){
        if(!StringUtils.hasText(value)){
            throw new ValidationException(
                    capitalize(fieldName)+" is required"
            );
        }

        try{
            return Enum.valueOf(
                    enumClass,
                    value.trim().toUpperCase(Locale.ROOT)
            );
        }catch(IllegalArgumentException exception){
            throw new ValidationException("Invalid "+fieldName+": "+value);
        }
    }

    private String capitalize(String value){
        return Character.toUpperCase(value.charAt(0))+value.substring(1);
    }

    private void validateProductValues(ProductRequest request){
        if(request.getMinimumSumInsured().compareTo(request.getMaximumSumInsured())>0){
            throw new ValidationException(
                    "Minimum sum insured cannot exceed maximum sum insured"
            );
        }

        if(request.getMinimumTermMonths()>request.getMaximumTermMonths()){
            throw new ValidationException(
                    "Minimum term cannot exceed maximum term"
            );
        }
    }

    private String validateCategory(String category){
        if(category==null||category.isBlank()){
            throw new ValidationException("Product category is required");
        }

        try{
            return ProductCategory.valueOf(category.trim().toUpperCase()).name();
        }catch(IllegalArgumentException e){
            throw new ValidationException(
                    "Category must be WEATHER, FLOOD, AGRICULTURE, BUSINESS_INTERRUPTION, TRAVEL, CYCLONE or OTHER"
            );
        }
    }

    private String validateStatus(String status){
        if(status==null||status.isBlank()){
            throw new ValidationException("Product status is required");
        }

        try{
            return ProductStatus.valueOf(status.trim().toUpperCase()).name();
        }catch(IllegalArgumentException e){
            throw new ValidationException(
                    "Status must be DRAFT, ACTIVE, INACTIVE or ARCHIVED"
            );
        }
    }

    private String normalizeCode(String code){
        return code.trim().toUpperCase();
    }

    private String trim(String value){
        return value==null||value.isBlank()?null:value.trim();
    }

    private String truncate(String value,int maxLength){
        if(value==null){
            return null;
        }

        return value.length()<=maxLength
                ?value
                :value.substring(0,maxLength);
    }

    private Flux<Coverage> saveCoverages(Long productId,List<CoverageRequest> requests){
        if(requests==null||requests.isEmpty()) return Flux.empty();

        Set<String> codes=new HashSet<>();

        for(CoverageRequest request:requests){
            String code=normalizeCode(request.getCoverageCode());

            if(!codes.add(code)){
                return Flux.error(new ValidationException(
                        "Duplicate coverage code: "+code
                ));
            }
        }

        return Flux.fromIterable(requests)
                .concatMap(request->{
                    LocalDateTime now=LocalDateTime.now();

                    Coverage coverage=Coverage.builder()
                            .productId(productId)
                            .coverageCode(normalizeCode(request.getCoverageCode()))
                            .name(request.getName().trim())
                            .description(trim(request.getDescription()))
                            .coverageLimit(request.getCoverageLimit())
                            .mandatory(Boolean.TRUE.equals(request.getMandatory()))
                            .active(request.getActive()==null||request.getActive())
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    return coverageRepository.save(coverage);
                });
    }

    public Mono<ProductResponse> getProduct(Long productId){
        log.debug("Get product: productId={}",productId);
        if(productId==null||productId<=0){
            return Mono.error(new ValidationException("Product id must be positive"));
        }

        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new NotFoundException("Product not found")))
                .flatMap(product->buildResponse(product,false));
    }

    public Mono<InternalProductResponse> getInternalProduct(Long productId){
        log.debug("Get internal product: productId={}",productId);
        if(productId==null||productId<=0){
            return Mono.error(new ValidationException("Product id must be positive"));
        }

        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new NotFoundException("Product not found")))
                .map(this::toInternalResponse);
    }

    public Flux<InternalProductResponse> getAllInternalProducts(){
        log.debug("Get all internal products");
        return productRepository.findAll()
                .map(this::toInternalResponse);
    }

    public Mono<InternalProductResponse> getInternalProductByCode(String productCode){
        log.debug("Get internal product by code");
        if(productCode==null||productCode.isBlank()){
            return Mono.error(new ValidationException("Product code is required"));
        }

        return productRepository.findByProductCodeIgnoreCase(productCode.trim())
                .switchIfEmpty(Mono.error(new NotFoundException("Product not found")))
                .map(this::toInternalResponse);
    }

    private InternalProductResponse toInternalResponse(Product product){
        return InternalProductResponse.builder()
                .productId(product.getId())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .category(product.getCategory())
                .status(product.getStatus())
                .perilCode(product.getPerilCode())
                .measurementType(product.getMeasurementType())
                .measurementUnit(product.getMeasurementUnit())
                .thresholdValue(product.getThresholdValue())
                .comparisonOperator(product.getComparisonOperator())
                .aggregationType(product.getAggregationType())
                .observationWindowHours(product.getObservationWindowHours())
                .minimumObservationCount(product.getMinimumObservationCount())
                .triggerDescription(product.getTriggerDescription())
                .minSumInsured(product.getMinimumSumInsured())
                .maxSumInsured(product.getMaximumSumInsured())
                .minTermMonths(product.getMinimumTermMonths())
                .maxTermMonths(product.getMaximumTermMonths())
                .onlineSale(ProductStatus.ACTIVE.name().equals(
                        product.getStatus()
                ))
                .build();
    }

    private ProductThresholdHistoryResponse toHistoryResponse(
            ProductThresholdHistory history){
        return ProductThresholdHistoryResponse.builder()
                .id(history.getId())
                .productId(history.getProductId())
                .productCode(history.getProductCode())
                .previousPerilCode(history.getPreviousPerilCode())
                .newPerilCode(history.getNewPerilCode())
                .previousMeasurementType(history.getPreviousMeasurementType())
                .newMeasurementType(history.getNewMeasurementType())
                .previousMeasurementUnit(history.getPreviousMeasurementUnit())
                .newMeasurementUnit(history.getNewMeasurementUnit())
                .previousThresholdValue(history.getPreviousThresholdValue())
                .newThresholdValue(history.getNewThresholdValue())
                .previousComparisonOperator(history.getPreviousComparisonOperator())
                .newComparisonOperator(history.getNewComparisonOperator())
                .previousAggregationType(history.getPreviousAggregationType())
                .newAggregationType(history.getNewAggregationType())
                .previousObservationWindowHours(history.getPreviousObservationWindowHours())
                .newObservationWindowHours(history.getNewObservationWindowHours())
                .previousMinimumObservationCount(history.getPreviousMinimumObservationCount())
                .newMinimumObservationCount(history.getNewMinimumObservationCount())
                .changedBy(history.getChangedBy())
                .changeReason(history.getChangeReason())
                .changedAt(history.getChangedAt())
                .triggerSyncStatus(history.getTriggerSyncStatus())
                .build();
    }

    private record TriggerSnapshotValues(
            String perilCode,
            String measurementType,
            String measurementUnit,
            BigDecimal thresholdValue,
            String comparisonOperator,
            String aggregationType,
            Integer observationWindowHours,
            Integer minimumObservationCount,
            String triggerDescription
    ){}
}