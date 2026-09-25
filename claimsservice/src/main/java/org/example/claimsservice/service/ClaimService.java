package org.example.claimsservice.service;

import lombok.RequiredArgsConstructor;
import org.example.claimsservice.dto.ApproveClaimRequest;
import org.example.claimsservice.dto.ClaimResponse;
import org.example.claimsservice.dto.ClaimTimelineResponse;
import org.example.claimsservice.dto.CreateClaimRequest;
import org.example.claimsservice.dto.MessageResponse;
import org.example.claimsservice.dto.RejectClaimRequest;
import org.example.claimsservice.dto.SettleClaimRequest;
import org.example.claimsservice.dto.InternalPolicyResponse;
import org.example.claimsservice.dto.InternalClaimResponse;
import org.example.claimsservice.dto.PolicyTriggerEligibilityResponse;
import org.example.claimsservice.exception.ConflictException;
import org.example.claimsservice.exception.DependentServiceException;
import org.example.claimsservice.exception.ForbiddenException;
import org.example.claimsservice.exception.NotFoundException;
import org.example.claimsservice.exception.ValidationException;
import org.example.claimsservice.model.Claim;
import org.example.claimsservice.model.ClaimSource;
import org.example.claimsservice.model.ClaimStatus;
import org.example.claimsservice.model.ClaimTimeline;
import org.example.claimsservice.model.TriggerDetectionStatus;
import org.example.claimsservice.repository.ClaimRepository;
import org.example.claimsservice.repository.ClaimTimelineRepository;
import org.springframework.beans.factory.annotation.Value;
import org.example.claimsservice.util.GatewayHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimService{
    private final ClaimRepository claimRepository;
    private final ClaimTimelineRepository claimTimelineRepository;
    private final WebClient.Builder webClientBuilder;
    private final TriggerEligibilityClient triggerEligibilityClient;
    private final NotificationService notificationService;
    private final FinancialServiceClient financialServiceClient;

    @Value("${gateway.internal-key}")
    private String internalGatewayKey;

    @Transactional
    public Mono<ClaimResponse> createClaim(Long customerId,CreateClaimRequest request){
        return getPolicy(request.getPolicyId())
                .flatMap(policy->{
                    validatePolicy(policy,customerId,request);

                    if(isManualException(request)){
                        return createManualExceptionClaim(
                                customerId,request,policy
                        );
                    }

                    return validateTriggerEligibility(policy,customerId)
                            .flatMap(eligibility->{
                                validateIncidentAgainstTrigger(request,eligibility);

                                return validateNoDuplicateTriggerClaim(
                                        customerId,
                                        policy.getId(),
                                        eligibility.getTriggerEventId()
                                ).then(saveAutomaticClaim(
                                        customerId,
                                        request,
                                        policy,
                                        eligibility
                                ));
                            });
                })
                .map(this::toResponse)
                .flatMap(resp -> notifySubmitted(
                        customerId,
                        resp.getClaimSource(),
                        resp.getId(),
                        resp.getClaimNumber()
                ).thenReturn(resp))
                .flatMap(resp -> notificationService.audit(
                        "CLAIM_SUBMITTED", "CLAIM", resp.getId(), null,
                        "Submitted claim " + resp.getClaimNumber() +
                                " for " + resp.getClaimedAmount()
                ).thenReturn(resp));
    }

    public Flux<ClaimResponse> getMyClaims(Long customerId,String status){
        if(status!=null&&!status.isBlank()){
            return claimRepository.findAllByCustomerIdAndStatus(
                    customerId,
                    validateStatus(status)
            ).map(this::toResponse);
        }

        return claimRepository.findAllByCustomerId(customerId)
                .map(this::toResponse);
    }

    public Mono<ClaimResponse> getMyClaim(Long customerId,Long claimId){
        return claimRepository.findByIdAndCustomerId(claimId,customerId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Claim not found")
                ))
                .map(this::toResponse);
    }

    @Transactional
    public Mono<MessageResponse> cancelClaim(Long customerId,Long claimId){
        return claimRepository.findByIdAndCustomerId(claimId,customerId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Claim not found")
                ))
                .flatMap(claim->{
                    if(!ClaimStatus.SUBMITTED.name().equals(claim.getStatus())){
                        return Mono.error(new ConflictException(
                                "Only submitted claims can be cancelled"
                        ));
                    }

                    String oldStatus=claim.getStatus();
                    claim.setStatus(ClaimStatus.CANCELLED.name());
                    claim.setUpdatedAt(LocalDateTime.now());
                    return claimRepository.save(claim)
                            .flatMap(saved->recordTimeline(
                                    saved,
                                    oldStatus,
                                    ClaimStatus.CANCELLED.name(),
                                    "CLAIM_CANCELLED",
                                    customerId,
                                    "CUSTOMER",
                                    "Customer cancelled the claim"
                            ).thenReturn(saved));
                })
                .flatMap(saved -> notificationService.notifyClaimCancelled(
                        customerId, saved.getId(), saved.getClaimNumber()
                ).thenReturn(saved))
                .flatMap(saved -> notificationService.audit(
                        "CLAIM_CANCELLED", "CLAIM", saved.getId(), null,
                        "Cancelled claim " + saved.getClaimNumber()
                ).thenReturn(saved))
                .thenReturn(MessageResponse.builder()
                        .message("Claim cancelled successfully")
                        .build());
    }

    public Flux<ClaimResponse> getSubmittedClaims(){
        return claimRepository.findAllByStatus(
                ClaimStatus.SUBMITTED.name()
        ).map(this::toResponse);
    }

    public Flux<ClaimResponse> getManualClaims(){
        return claimRepository.findAllByManualReviewRequiredTrue()
                .map(this::toResponse);
    }

    public Mono<ClaimResponse> getManualClaim(Long claimId){
        return claimRepository.findByIdAndManualReviewRequiredTrue(claimId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Manual claim not found")
                ))
                .map(this::toResponse);
    }

    public Flux<ClaimResponse> getAllClaims(String status,Long customerId){
        Flux<Claim> claims=(status!=null&&!status.isBlank())
                ?claimRepository.findAllByStatus(validateStatus(status))
                :claimRepository.findAll();

        if(customerId!=null){
            claims=claims.filter(claim->customerId.equals(claim.getCustomerId()));
        }

        return claims.map(this::toResponse);
    }

    public Mono<ClaimResponse> getClaim(Long claimId){
        return claimRepository.findById(claimId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Claim not found")
                ))
                .map(this::toResponse);
    }

    public Mono<InternalClaimResponse> getInternalClaim(Long claimId){
        return claimRepository.findById(claimId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Claim not found")
                ))
                .map(this::toInternalResponse);
    }

    @Transactional
    public Mono<org.example.claimsservice.dto.ClaimCreationResponse> createClaimsFromTriggerEvent(
            org.example.claimsservice.dto.ClaimCreationRequest request){
        if(request.getTriggerEventId()==null||request.getEligiblePolicies()==null){
            return Mono.error(new ValidationException("Trigger event and matched policies are required"));
        }
        return Flux.fromIterable(request.getEligiblePolicies())
                .concatMap(policy->claimRepository.existsByPolicyIdAndTriggerEventId(
                                policy.getPolicyId(),request.getTriggerEventId())
                        .flatMap(existing->{
                            if(existing){
                                return Mono.just(org.example.claimsservice.dto.ClaimCreationResponse.ClaimResult.builder()
                                        .policyId(policy.getPolicyId()).policyNumber(policy.getPolicyNumber())
                                        .status("EXISTING").build());
                            }
                            LocalDateTime now=LocalDateTime.now();
                            Claim claim=Claim.builder()
                                    .claimNumber(generateClaimNumber())
                                    .customerId(policy.getCustomerId()).policyId(policy.getPolicyId())
                                    .policyNumber(policy.getPolicyNumber()).productId(request.getProductId())
                                    .triggerEventId(request.getTriggerEventId())
                                    .triggerEventReference(request.getTriggerEventReference())
                                    .locationZoneId(request.getLocationZoneId())
                                    .perilId(request.getPerilId())
                                    .incidentType(request.getMeasurementType())
                                    .incidentDate(LocalDate.now()).incidentLocation("AUTO")
                                    .description("Automatic claim created from a verified trigger event")
                                    .claimedAmount(policy.getSumInsured())
                                    .status(ClaimStatus.SUBMITTED.name())
                                    .claimSource(ClaimSource.AUTO_TRIGGERED.name())
                                    .manualReviewRequired(false).createdFromTriggerEvent(true)
                                    .triggerDetectionStatus(TriggerDetectionStatus.DETECTED.name())
                                    .submittedAt(now).updatedAt(now).payoutRequested(false).build();
                            return claimRepository.save(claim)
                                    .flatMap(saved->recordTimeline(saved,null,ClaimStatus.SUBMITTED.name(),
                                            "CLAIM_SUBMITTED",policy.getCustomerId(),"TRIGGER_SERVICE",
                                            "Automatic claim created from trigger event").thenReturn(saved))
                                    .flatMap(saved->notificationService.notifyClaimSubmitted(
                                            saved.getCustomerId(),saved.getId(),saved.getClaimNumber())
                                            .then(notificationService.notifyClaimPendingReview(
                                                    saved.getId(),saved.getClaimNumber()))
                                            .thenReturn(saved))
                                    .map(saved->org.example.claimsservice.dto.ClaimCreationResponse.ClaimResult.builder()
                                            .policyId(policy.getPolicyId()).policyNumber(policy.getPolicyNumber())
                                            .claimId(saved.getId()).claimNumber(saved.getClaimNumber()).status("CREATED").build());
                        })
                        .onErrorResume(error->Mono.just(org.example.claimsservice.dto.ClaimCreationResponse.ClaimResult.builder()
                                .policyId(policy.getPolicyId()).policyNumber(policy.getPolicyNumber())
                                .status("FAILED").errorMessage(error.getMessage()).build())))
                .collectList()
                .map(results->org.example.claimsservice.dto.ClaimCreationResponse.builder()
                        .claimsCreated((int)results.stream().filter(r->"CREATED".equals(r.getStatus())).count())
                        .claimsFailed((int)results.stream().filter(r->"FAILED".equals(r.getStatus())).count())
                        .results(results).build());
    }
    
        @Transactional
        public Mono<InternalClaimResponse> createInternalClaim(CreateClaimRequest request){
                return getPolicy(request.getPolicyId())
                                .flatMap(policy -> {
                                        if(policy.getId()==null){
                                                return Mono.error(new ValidationException("Policy information is incomplete"));
                                        }

                                        if(!"ACTIVE".equalsIgnoreCase(policy.getStatus())){
                                                return Mono.error(new ValidationException("Policy must be ACTIVE to create an automatic claim"));
                                        }

                                        return triggerEligibilityClient.getEligibility(policy.getId())
                                                        .flatMap(eligibility -> {
                                                                if(!eligibility.isEligible()){
                                                                        return Mono.error(new ValidationException("Policy is not eligible for trigger-based claim: "+eligibility.getEligibilityReason()));
                                                                }

                                                                Long customerId = policy.getCustomerId();

                                                                return validateNoDuplicateTriggerClaim(
                                                                                customerId,
                                                                                policy.getId(),
                                                                                eligibility.getTriggerEventId()
                                                                ).then(saveAutomaticClaim(
                                                                                customerId,
                                                                                request,
                                                                                policy,
                                                                                eligibility
                                                                )).map(this::toInternalResponse);
                                                        });
                                });
                        }

    @Transactional
    public Mono<ClaimResponse> startReview(Long reviewerId,Long claimId){
        return claimRepository.findById(claimId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Claim not found")
                ))
                .flatMap(claim->{
                    requireStatus(
                            claim,
                            ClaimStatus.SUBMITTED,
                            "Only submitted claims can be reviewed"
                    );

                    String oldStatus=claim.getStatus();
                    LocalDateTime now=LocalDateTime.now();

                    claim.setStatus(ClaimStatus.UNDER_REVIEW.name());
                    claim.setReviewedBy(reviewerId);
                    claim.setReviewedAt(now);
                    claim.setUpdatedAt(now);

                    return claimRepository.save(claim)
                            .flatMap(saved->recordTimeline(
                                    saved,
                                    oldStatus,
                                    ClaimStatus.UNDER_REVIEW.name(),
                                    "REVIEW_STARTED",
                                    reviewerId,
                                    "CLAIMS_OFFICER",
                                    "Claims officer started review"
                            ).thenReturn(saved));
                })
                .flatMap(saved -> notificationService.notifyClaimUnderReview(
                        saved.getCustomerId(), saved.getId(), saved.getClaimNumber()
                ).thenReturn(saved))
                .flatMap(saved -> notificationService.audit(
                        "CLAIM_REVIEW_STARTED", "CLAIM", saved.getId(), reviewerId,
                        "Started review of claim " + saved.getClaimNumber()
                ).thenReturn(saved))
                .map(this::toResponse);
    }

    @Transactional
    public Mono<ClaimResponse> approveClaim(
            Long reviewerId,
            Long claimId,
            ApproveClaimRequest request
    ){
        return claimRepository.findById(claimId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Claim not found")
                ))
                .flatMap(claim->{
                    requireStatus(
                            claim,
                            ClaimStatus.UNDER_REVIEW,
                            "Only claims under review can be approved"
                    );

                    if(request.getApprovedAmount()==null){
                        return Mono.error(new ValidationException(
                                "Approved amount is required"
                        ));
                    }

                    if(request.getApprovedAmount().signum()<=0){
                        return Mono.error(new ValidationException(
                                "Approved amount must be greater than zero"
                        ));
                    }

                    if(request.getApprovedAmount()
                            .compareTo(claim.getClaimedAmount())>0){
                        return Mono.error(new ValidationException(
                                "Approved amount cannot exceed claimed amount"
                        ));
                    }

                    String oldStatus=claim.getStatus();
                    LocalDateTime now=LocalDateTime.now();

                    claim.setStatus(ClaimStatus.APPROVED.name());
                    claim.setApprovedAmount(request.getApprovedAmount());
                    claim.setRejectionReason(null);
                    claim.setReviewedBy(reviewerId);
                    claim.setReviewedAt(now);
                    claim.setUpdatedAt(now);

                    return claimRepository.save(claim)
                            .flatMap(saved->recordTimeline(
                                    saved,
                                    oldStatus,
                                    ClaimStatus.APPROVED.name(),
                                    "CLAIM_APPROVED",
                                    reviewerId,
                                    "CLAIMS_OFFICER",
                                    "Claim approved with amount: "+request.getApprovedAmount()
                            )                            .thenReturn(saved));
                })
                .flatMap(saved -> notificationService.notifyClaimApproved(
                        saved.getCustomerId(), saved.getId(), saved.getClaimNumber()
                ).thenReturn(saved))
                .flatMap(saved -> notificationService.audit(
                        "CLAIM_APPROVED", "CLAIM", saved.getId(), reviewerId,
                        "Approved claim " + saved.getClaimNumber() +
                                " for " + saved.getApprovedAmount()
                ).thenReturn(saved))
                .map(this::toResponse);
    }

    // Must run after approveClaim's transaction commits: the financial
    // service reads the claim back and rejects it unless it is APPROVED.
    public Mono<Void> requestPayout(Long claimId){
        return financialServiceClient.createPayoutForClaim(claimId);
    }

    @Transactional
    public Mono<ClaimResponse> rejectClaim(
            Long reviewerId,
            Long claimId,
            RejectClaimRequest request
    ){
        return claimRepository.findById(claimId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Claim not found")
                ))
                .flatMap(claim->{
                    requireStatus(
                            claim,
                            ClaimStatus.UNDER_REVIEW,
                            "Only claims under review can be rejected"
                    );

                    if(request.getReason()==null||
                            request.getReason().isBlank()){
                        return Mono.error(new ValidationException(
                                "Rejection reason is required"
                        ));
                    }

                    String oldStatus=claim.getStatus();
                    LocalDateTime now=LocalDateTime.now();

                    claim.setStatus(ClaimStatus.REJECTED.name());
                    claim.setApprovedAmount(null);
                    claim.setRejectionReason(request.getReason().trim());
                    claim.setReviewedBy(reviewerId);
                    claim.setReviewedAt(now);
                    claim.setUpdatedAt(now);

                    return claimRepository.save(claim)
                            .flatMap(saved->recordTimeline(
                                    saved,
                                    oldStatus,
                                    ClaimStatus.REJECTED.name(),
                                    "CLAIM_REJECTED",
                                    reviewerId,
                                    "CLAIMS_OFFICER",
                                    "Claim rejected: "+request.getReason().trim()
                            )                            .thenReturn(saved));
                })
                .flatMap(saved -> notificationService.notifyClaimRejected(
                        saved.getCustomerId(), saved.getId(), saved.getClaimNumber(), request.getReason().trim()
                ).thenReturn(saved))
                .flatMap(saved -> notificationService.audit(
                        "CLAIM_REJECTED", "CLAIM", saved.getId(), reviewerId,
                        "Rejected claim " + saved.getClaimNumber() +
                                ": " + request.getReason().trim()
                ).thenReturn(saved))
                .map(this::toResponse);
    }

    @Transactional
    public Mono<ClaimResponse> settleClaim(
            Long officerId,
            Long claimId,
            SettleClaimRequest request
    ){
        return claimRepository.findById(claimId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Claim not found")
                ))
                .flatMap(claim->{
                    requireStatus(
                            claim,
                            ClaimStatus.APPROVED,
                            "Only approved claims can be settled"
                    );

                    String oldStatus=claim.getStatus();
                    LocalDateTime now=LocalDateTime.now();

                    claim.setStatus(ClaimStatus.SETTLED.name());
                    claim.setUpdatedAt(now);

                    String notes=request!=null&&request.getNotes()!=null
                            ?request.getNotes().trim()
                            :"Claim settled";

                    return claimRepository.save(claim)
                            .flatMap(saved->recordTimeline(
                                    saved,
                                    oldStatus,
                                    ClaimStatus.SETTLED.name(),
                                    "CLAIM_SETTLED",
                                    officerId,
                                    "CLAIMS_OFFICER",
                                    notes
                            )                            .thenReturn(saved));
                })
                .flatMap(saved -> notificationService.notifyClaimSettled(
                        saved.getCustomerId(), saved.getId(), saved.getClaimNumber()
                ).thenReturn(saved))
                .flatMap(saved -> notificationService.audit(
                        "CLAIM_SETTLED", "CLAIM", saved.getId(), officerId,
                        "Settled claim " + saved.getClaimNumber()
                ).thenReturn(saved))
                .map(this::toResponse);
    }

    private Mono<InternalPolicyResponse> getPolicy(Long policyId){
        if(policyId==null||policyId<=0){
            return Mono.error(new ValidationException(
                    "Policy ID must be greater than zero"
            ));
        }

        return webClientBuilder.build()
                .get()
                .uri(
                        "http://PRODUCT-SERVICE/internal/policies/{id}",
                        policyId
                )
                .header(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .header(GatewayHeaders.GATEWAY_KEY, internalGatewayKey)
                .retrieve()
                .onStatus(
                        status->status.value()==404,
                        response->Mono.error(
                                new NotFoundException("Policy not found")
                        )
                )
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Internal policy validation failed"
                                )
                        )
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Product service is currently unavailable"
                                )
                        )
                )
                .bodyToMono(InternalPolicyResponse.class)
                .switchIfEmpty(Mono.error(
                        new DependentServiceException(
                                "Product service returned an empty policy response"
                        )
                ))
                .onErrorMap(
                        WebClientRequestException.class,
                        exception->new DependentServiceException(
                                "Product service is currently unavailable"
                        )
                );
    }

    private void validatePolicy(
            InternalPolicyResponse policy,
            Long customerId,
            CreateClaimRequest request
    ){
        if(policy.getId()==null){
            throw new ValidationException(
                    "Policy information is incomplete"
            );
        }

        if(policy.getCustomerId()==null||
                !policy.getCustomerId().equals(customerId)){
            throw new ForbiddenException(
                    "Policy does not belong to the authenticated customer"
            );
        }

        if(!ClaimStatusValue.ACTIVE.equalsIgnoreCase(policy.getStatus())){
            throw new ConflictException(
                    "Claims can be submitted only for active policies"
            );
        }

        if(request.getClaimedAmount()==null){
            throw new ValidationException(
                    "Claimed amount is required"
            );
        }

        if(request.getClaimedAmount().signum()<=0){
            throw new ValidationException(
                    "Claimed amount must be greater than zero"
            );
        }

        if(policy.getSumInsured()==null){
            throw new ValidationException(
                    "Policy sum insured is unavailable"
            );
        }

        if(request.getClaimedAmount()
                .compareTo(policy.getSumInsured())>0){
            throw new ValidationException(
                    "Claimed amount cannot exceed policy sum insured"
            );
        }

        if(request.getIncidentDate()==null){
            throw new ValidationException(
                    "Incident date is required"
            );
        }

        if(request.getIncidentDate().isAfter(LocalDate.now())){
            throw new ValidationException(
                    "Incident date cannot be in the future"
            );
        }

        if(policy.getStartDate()!=null&&
                request.getIncidentDate().isBefore(
                        policy.getStartDate()
                )){
            throw new ValidationException(
                    "Incident date cannot be before policy start date"
            );
        }

        if(policy.getEndDate()!=null&&
                request.getIncidentDate().isAfter(
                        policy.getEndDate()
                )){
            throw new ValidationException(
                    "Incident date cannot be after policy end date"
            );
        }

        if(request.getIncidentType()==null||
                request.getIncidentType().isBlank()){
            throw new ValidationException(
                    "Incident type is required"
            );
        }

        if(request.getIncidentLocation()==null||
                request.getIncidentLocation().isBlank()){
            throw new ValidationException(
                    "Incident location is required"
            );
        }

        if(request.getDescription()==null||
                request.getDescription().isBlank()){
            throw new ValidationException(
                    "Claim description is required"
            );
        }
    }

    private Mono<PolicyTriggerEligibilityResponse> validateTriggerEligibility(
            InternalPolicyResponse policy,
            Long authenticatedCustomerId
    ){
        return triggerEligibilityClient.getEligibility(policy.getId())
                .flatMap(eligibility->{
                    if(eligibility.getPolicyId()==null||
                            !eligibility.getPolicyId().equals(
                                    policy.getId()
                            )){
                        return Mono.error(new ValidationException(
                                "Trigger eligibility does not match the selected policy"
                        ));
                    }

                    if(eligibility.getCustomerId()==null||
                            !eligibility.getCustomerId().equals(
                                    authenticatedCustomerId
                            )){
                        return Mono.error(new ForbiddenException(
                                "Trigger eligibility does not belong to the authenticated customer"
                        ));
                    }

                    if(!eligibility.isEligible()){
                        String reason=eligibility.getEligibilityReason();

                        if(reason==null||reason.isBlank()){
                            reason="No verified trigger event applies to this policy";
                        }

                        return Mono.error(new ValidationException(
                                "Claim is not eligible: "+reason
                        ));
                    }

                    if(eligibility.getTriggerEventId()==null||
                            eligibility.getTriggerEventReference()==null||
                            eligibility.getTriggerEventReference().isBlank()){
                        return Mono.error(new ValidationException(
                                "Verified trigger event information is unavailable"
                        ));
                    }

                    return Mono.just(eligibility);
                });
    }

    private void validateIncidentAgainstTrigger(
            CreateClaimRequest request,
            PolicyTriggerEligibilityResponse eligibility
    ){
        if(eligibility.getEventWindowStart()==null||
                eligibility.getEventWindowEnd()==null){
            throw new ValidationException(
                    "Trigger event coverage period is unavailable"
            );
        }

        LocalDate incidentDate=request.getIncidentDate();
        LocalDate eventStart=eligibility
                .getEventWindowStart()
                .toLocalDate();
        LocalDate eventEnd=eligibility
                .getEventWindowEnd()
                .toLocalDate();

        if(incidentDate.isBefore(eventStart)||
                incidentDate.isAfter(eventEnd)){
            throw new ValidationException(
                    "Incident date must fall within the verified trigger event period"
            );
        }
    }

    private boolean isManualException(CreateClaimRequest request){
        return request.getClaimSource()!=null&&
                ClaimSource.MANUAL_EXCEPTION.name()
                        .equalsIgnoreCase(request.getClaimSource().trim());
    }

    private Mono<Claim> createManualExceptionClaim(
            Long customerId,
            CreateClaimRequest request,
            InternalPolicyResponse policy
    ){
        if(!Boolean.TRUE.equals(request.getCustomerConfirmedLoss())){
            return Mono.error(new ValidationException(
                    "Customer must confirm the actual loss occurred"
            ));
        }

        if(request.getEvidenceDescription()==null||
                request.getEvidenceDescription().isBlank()){
            return Mono.error(new ValidationException(
                    "Evidence description is required for a manual exception claim"
            ));
        }

        return validateNoDuplicateManualClaim(
                customerId,
                policy.getId(),
                request.getIncidentDate()
        ).then(doSaveManualClaim(customerId,request,policy));
    }

    private Mono<Void> validateNoDuplicateManualClaim(
            Long customerId,
            Long policyId,
            LocalDate incidentDate
    ){
        return claimRepository
                .existsByCustomerIdAndPolicyIdAndIncidentDateAndStatusNot(
                        customerId,
                        policyId,
                        incidentDate,
                        ClaimStatus.CANCELLED
                )
                .flatMap(exists->{
                    if(exists){
                        return Mono.error(new ConflictException(
                                "A manual claim already exists for this policy and incident date"
                        ));
                    }

                    return Mono.empty();
                });
    }

    private Mono<Void> validateNoDuplicateTriggerClaim(
            Long customerId,
            Long policyId,
            Long triggerEventId
    ){
        return claimRepository
                .existsByCustomerIdAndPolicyIdAndTriggerEventIdAndStatusNot(
                        customerId,
                        policyId,
                        triggerEventId,
                        ClaimStatus.CANCELLED
                )
                .flatMap(exists->{
                    if(exists){
                        return Mono.error(new ConflictException(
                                "A claim already exists for this policy and trigger event"
                        ));
                    }

                    return Mono.empty();
                });
    }

    private Mono<Claim> doSaveManualClaim(
            Long customerId,
            CreateClaimRequest request,
            InternalPolicyResponse policy
    ){
        LocalDateTime now=LocalDateTime.now();

        Claim claim=Claim.builder()
                .claimNumber(generateClaimNumber())
                .customerId(customerId)
                .policyId(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .productId(policy.getProductId())
                .locationZoneId(policy.getLocationZoneId())
                .incidentType(
                        request.getIncidentType()
                                .trim()
                                .toUpperCase(Locale.ROOT)
                )
                .incidentDate(request.getIncidentDate())
                .incidentLocation(
                        request.getIncidentLocation().trim()
                )
                .description(request.getDescription().trim())
                .claimedAmount(request.getClaimedAmount())
                .status(ClaimStatus.SUBMITTED.name())
                .claimSource(ClaimSource.MANUAL_EXCEPTION.name())
                .manualReviewRequired(true)
                .triggerDetectionStatus(
                        TriggerDetectionStatus.UNDER_INVESTIGATION.name()
                )
                .customerConfirmedLoss(true)
                .createdFromTriggerEvent(false)
                .payoutRequested(false)
                .evidenceDescription(
                        request.getEvidenceDescription()!=null
                                ?request.getEvidenceDescription().trim()
                                :null
                )
                .submittedAt(now)
                .updatedAt(now)
                .build();

        return claimRepository.save(claim)
                .flatMap(saved->recordTimeline(
                        saved,
                        null,
                        ClaimStatus.SUBMITTED.name(),
                        "MANUAL_CLAIM_SUBMITTED",
                        customerId,
                        "CUSTOMER",
                        "Customer reported a missed trigger event for manual review"
                ).thenReturn(saved));
    }

    private Mono<Claim> saveAutomaticClaim(
            Long customerId,
            CreateClaimRequest request,
            InternalPolicyResponse policy,
            PolicyTriggerEligibilityResponse eligibility
    ){
        LocalDateTime now=LocalDateTime.now();

        Claim claim=Claim.builder()
                .claimNumber(generateClaimNumber())
                .customerId(customerId)
                .policyId(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .productId(policy.getProductId())
                .triggerEventId(eligibility.getTriggerEventId())
                .triggerEventReference(
                        eligibility.getTriggerEventReference()
                )
                .triggerDefinitionId(
                        eligibility.getTriggerDefinitionId()
                )
                .perilId(eligibility.getPerilId())
                .locationZoneId(eligibility.getLocationZoneId())
                .incidentType(
                        request.getIncidentType()
                                .trim()
                                .toUpperCase(Locale.ROOT)
                )
                .incidentDate(request.getIncidentDate())
                .incidentLocation(
                        request.getIncidentLocation().trim()
                )
                .description(request.getDescription().trim())
                .claimedAmount(request.getClaimedAmount())
                .status(ClaimStatus.SUBMITTED.name())
                .claimSource(ClaimSource.AUTO_TRIGGERED.name())
                .manualReviewRequired(false)
                .triggerDetectionStatus(
                        TriggerDetectionStatus.DETECTED.name()
                )
                .customerConfirmedLoss(true)
                .createdFromTriggerEvent(true)
                .payoutRequested(false)
                .submittedAt(now)
                .updatedAt(now)
                .build();

        return claimRepository.save(claim)
                .flatMap(saved->recordTimeline(
                        saved,
                        null,
                        ClaimStatus.SUBMITTED.name(),
                        "CLAIM_SUBMITTED",
                        customerId,
                        "CUSTOMER",
                        "Customer submitted claim"
                ).thenReturn(saved));
    }

    private Mono<Void> notifySubmitted(
            Long customerId,
            String claimSource,
            Long claimId,
            String claimNumber
    ){
        Mono<Void> customerNotification;
        if(claimSource!=null&&
                ClaimSource.MANUAL_EXCEPTION.name()
                        .equals(claimSource)){
            customerNotification = notificationService.notifyManualClaimSubmitted(
                    customerId,claimId,claimNumber
            );
        }else{
            customerNotification = notificationService.notifyClaimSubmitted(
                    customerId,claimId,claimNumber
            );
        }

        return customerNotification.then(
                notificationService.notifyClaimPendingReview(claimId,claimNumber)
        );
    }

    private Mono<Void> recordTimeline(
            Claim claim,
            String oldStatus,
            String newStatus,
            String action,
            Long actionBy,
            String actionByRole,
            String notes
    ){
        ClaimTimeline timeline=ClaimTimeline.builder()
                .claimId(claim.getId())
                .claimNumber(claim.getClaimNumber())
                .customerId(claim.getCustomerId())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .action(action)
                .actionBy(actionBy)
                .actionByRole(actionByRole)
                .notes(notes)
                .createdAt(LocalDateTime.now())
                .build();

        return claimTimelineRepository.save(timeline).then();
    }

    private void requireStatus(
            Claim claim,
            ClaimStatus expected,
            String message
    ){
        if(!expected.name().equals(claim.getStatus())){
            throw new ConflictException(message);
        }
    }

    private String validateStatus(String status){
        try{
            return ClaimStatus.valueOf(
                    status.trim().toUpperCase(Locale.ROOT)
            ).name();
        }catch(Exception exception){
            throw new ValidationException(
                    "Status must be SUBMITTED, UNDER_REVIEW, APPROVED, SETTLED, REJECTED or CANCELLED"
            );
        }
    }

    public Flux<ClaimTimelineResponse> getClaimTimeline(Long customerId,Long claimId){
        return claimRepository.findByIdAndCustomerId(claimId,customerId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Claim not found")
                ))
                .flatMapMany(claim->claimTimelineRepository
                        .findAllByClaimIdOrderByCreatedAtAsc(claim.getId())
                        .map(this::toTimelineResponse));
    }

    public Flux<ClaimTimelineResponse> getClaimTimelineAdmin(Long claimId){
        return claimRepository.findById(claimId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Claim not found")
                ))
                .flatMapMany(claim->claimTimelineRepository
                        .findAllByClaimIdOrderByCreatedAtAsc(claim.getId())
                        .map(this::toTimelineResponse));
    }

    private ClaimTimelineResponse toTimelineResponse(ClaimTimeline timeline){
        return ClaimTimelineResponse.builder()
                .id(timeline.getId())
                .oldStatus(timeline.getOldStatus())
                .newStatus(timeline.getNewStatus())
                .action(timeline.getAction())
                .actionBy(timeline.getActionBy())
                .actionByRole(timeline.getActionByRole())
                .notes(timeline.getNotes())
                .createdAt(timeline.getCreatedAt())
                .build();
    }

    private String generateClaimNumber(){
        String value=UUID.randomUUID()
                .toString()
                .replace("-","")
                .substring(0,8)
                .toUpperCase(Locale.ROOT);

        return "CLM-"+Year.now().getValue()+"-"+value;
    }

    private ClaimResponse toResponse(Claim claim){
        return ClaimResponse.builder()
                .id(claim.getId())
                .claimNumber(claim.getClaimNumber())
                .customerId(claim.getCustomerId())
                .policyId(claim.getPolicyId())
                .policyNumber(claim.getPolicyNumber())
                .productId(claim.getProductId())
                .triggerEventId(claim.getTriggerEventId())
                .triggerEventReference(
                        claim.getTriggerEventReference()
                )
                .triggerDefinitionId(
                        claim.getTriggerDefinitionId()
                )
                .perilId(claim.getPerilId())
                .locationZoneId(claim.getLocationZoneId())
                .incidentType(claim.getIncidentType())
                .incidentDate(claim.getIncidentDate())
                .incidentLocation(claim.getIncidentLocation())
                .description(claim.getDescription())
                .claimedAmount(claim.getClaimedAmount())
                .approvedAmount(claim.getApprovedAmount())
                .status(claim.getStatus())
                .rejectionReason(claim.getRejectionReason())
                .reviewedBy(claim.getReviewedBy())
                .reviewedAt(claim.getReviewedAt())
                .submittedAt(claim.getSubmittedAt())
                .updatedAt(claim.getUpdatedAt())
                .claimSource(claim.getClaimSource())
                .manualReviewRequired(claim.getManualReviewRequired())
                .triggerDetectionStatus(claim.getTriggerDetectionStatus())
                .manualReviewReason(claim.getManualReviewReason())
                .evidenceDescription(claim.getEvidenceDescription())
                .customerConfirmedLoss(claim.getCustomerConfirmedLoss())
                .createdFromTriggerEvent(claim.getCreatedFromTriggerEvent())
                .investigationResult(claim.getInvestigationResult())
                .investigatedBy(claim.getInvestigatedBy())
                .investigatedAt(claim.getInvestigatedAt())
                .payoutRequested(claim.getPayoutRequested())
                .build();
    }

    private InternalClaimResponse toInternalResponse(Claim claim){
        return InternalClaimResponse.builder()
                .claimId(claim.getId())
                .claimNumber(claim.getClaimNumber())
                .customerId(claim.getCustomerId())
                .policyId(claim.getPolicyId())
                .policyNumber(claim.getPolicyNumber())
                .productId(claim.getProductId())
                .triggerEventId(claim.getTriggerEventId())
                .status(claim.getStatus())
                .approvedAmount(claim.getApprovedAmount())
                .claimSource(claim.getClaimSource())
                .manualReviewRequired(claim.getManualReviewRequired())
                .build();
    }

    private static final class ClaimStatusValue{
        private static final String ACTIVE="ACTIVE";

        private ClaimStatusValue(){}
    }
}