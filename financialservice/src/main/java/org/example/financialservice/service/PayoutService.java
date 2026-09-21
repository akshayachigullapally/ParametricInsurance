package org.example.financialservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.example.financialservice.dto.CreatePayoutRequest;
import org.example.financialservice.dto.PayoutActionRequest;
import org.example.financialservice.dto.PayoutResponse;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.model.Payout;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.repository.PayoutRepository;
import org.example.financialservice.util.SecurityUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PayoutService{
    private final PayoutRepository payoutRepository;
    private final ClaimsServiceClient claimsServiceClient;
    private final TriggerServiceClient triggerServiceClient;
    private final NotificationService notifications;
    private final FinancialTransactionService financialTransactions;

    public PayoutService(
            PayoutRepository payoutRepository,
            ClaimsServiceClient claimsServiceClient,
            TriggerServiceClient triggerServiceClient,
            NotificationService notifications,
            FinancialTransactionService financialTransactions
    ){
        this.payoutRepository=payoutRepository;
        this.claimsServiceClient=claimsServiceClient;
        this.triggerServiceClient=triggerServiceClient;
        this.notifications=notifications;
        this.financialTransactions=financialTransactions;
    }

    public Mono<PayoutResponse> create(CreatePayoutRequest request){
        return claimsServiceClient.findClaim(request.getClaimId())
                .flatMap(claim->{
                    validateClaim(claim.getStatus(),
                            claim.getApprovedAmount(),
                            claim.getCustomerId(),
                            claim.getPolicyId());

                    return payoutRepository
                            .existsByClaimId(claim.getClaimId())
                            .flatMap(exists->exists
                                    ?Mono.error(new ConflictException(
                                    "Payout already exists for claim"
                            ))
                                    :Mono.just(claim));
                })
                .flatMap(claim->{
                    Payout payout=Payout.builder()
                            .payoutReference(generateReference())
                            .customerId(claim.getCustomerId())
                            .claimId(claim.getClaimId())
                            .claimNumber(claim.getClaimNumber())
                            .policyId(claim.getPolicyId())
                            .triggerEventId(claim.getTriggerEventId())
                            .amount(claim.getApprovedAmount())
                            .status(PayoutStatus.PENDING)
                            .retryCount(0)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return payoutRepository.save(payout);
                })
                .flatMap(saved->notifications.audit(
                        "PAYOUT_CREATED",
                        "PAYOUT",
                        saved.getId(),
                        null,
                        "claimId="+saved.getClaimId()+
                                ",amount="+saved.getAmount()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyPayoutPending(
                                saved.getCustomerId(),
                                saved.getPayoutReference(),
                                saved.getAmount()
                        )
                        .thenReturn(saved))
                .flatMap(saved->financialTransactions
                        .recordPayout(
                                saved.getCustomerId(),
                                saved.getPolicyId(),
                                saved.getId(),
                                saved.getClaimId(),
                                saved.getAmount(),
                                "PENDING"
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Mono<PayoutResponse> findById(Long payoutId){
        return SecurityUtil.requireCustomer()
                .flatMap(customerId->payoutRepository
                        .findByIdAndCustomerId(
                                payoutId,
                                customerId
                        )
                )
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Payout not found")
                ))
                .map(this::response);
    }

    public Flux<PayoutResponse> findMine(){
        return SecurityUtil.requireCustomer()
                .flatMapMany(customerId->payoutRepository
                        .findAllByCustomerIdOrderByCreatedAtDesc(
                                customerId
                        )
                )
                .map(this::response);
    }

    public Flux<PayoutResponse> findAll(){
        return SecurityUtil.requireAdmin()
                .thenMany(payoutRepository
                        .findAllByOrderByCreatedAtDesc()
                )
                .map(this::response);
    }

    public Flux<PayoutResponse> findByClaim(Long claimId){
        return payoutRepository
                .findAllByClaimIdOrderByCreatedAtDesc(claimId)
                .map(this::response);
    }

    public Mono<PayoutResponse> process(
            Long payoutId,
            PayoutActionRequest request
    ){
        return SecurityUtil.requireAdmin()
                .then(findEntity(payoutId))
                .flatMap(payout->{
                    if(payout.getStatus()!=PayoutStatus.PENDING){
                        return Mono.error(
                                new ConflictException(
                                        "Only pending payouts can be processed"
                                )
                        );
                    }

                    payout.setStatus(PayoutStatus.PROCESSING);
                    payout.setProcessedAt(LocalDateTime.now());
                    payout.setUpdatedAt(LocalDateTime.now());

                    return payoutRepository.save(payout);
                })
                .flatMap(saved->notifications.audit(
                        "PAYOUT_PROCESSING",
                        "PAYOUT",
                        saved.getId(),
                        null,
                        "payoutReference="+
                                saved.getPayoutReference()
                ).thenReturn(saved))
                .map(this::response);
    }

    public Mono<PayoutResponse> success(
            Long payoutId,
            PayoutActionRequest request
    ){
        return SecurityUtil.requireAdmin()
                .then(findEntity(payoutId))
                .flatMap(payout->{
                    if(payout.getStatus()!=PayoutStatus.PROCESSING){
                        return Mono.error(
                                new ConflictException(
                                        "Only processing payouts can succeed"
                                )
                        );
                    }

                    payout.setStatus(PayoutStatus.SUCCESS);
                    payout.setUpdatedAt(LocalDateTime.now());

                    if(request.getExternalReference()!=null){
                        payout.setExternalReference(
                                request.getExternalReference()
                        );
                    }

                    return payoutRepository.save(payout);
                })
                .flatMap(saved->notifications.audit(
                        "PAYOUT_SUCCESS",
                        "PAYOUT",
                        saved.getId(),
                        null,
                        "payoutReference="+
                                saved.getPayoutReference()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyPayoutProcessed(
                                saved.getCustomerId(),
                                saved.getPayoutReference(),
                                saved.getAmount()
                        )
                        .thenReturn(saved))
                .flatMap(saved->{
                    if(saved.getTriggerEventId()!=null){
                        return triggerServiceClient
                                .markEventProcessed(
                                        saved.getTriggerEventId()
                                )
                                .thenReturn(saved);
                    }
                    return Mono.just(saved);
                })
                .flatMap(saved->financialTransactions
                        .recordPayout(
                                saved.getCustomerId(),
                                saved.getPolicyId(),
                                saved.getId(),
                                saved.getClaimId(),
                                saved.getAmount(),
                                "SUCCESS"
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Mono<PayoutResponse> fail(
            Long payoutId,
            PayoutActionRequest request
    ){
        return SecurityUtil.requireAdmin()
                .then(findEntity(payoutId))
                .flatMap(payout->{
                    if(payout.getStatus()!=PayoutStatus.PROCESSING){
                        return Mono.error(
                                new ConflictException(
                                        "Only processing payouts can fail"
                                )
                        );
                    }

                    payout.setStatus(PayoutStatus.FAILED);
                    payout.setUpdatedAt(LocalDateTime.now());

                    if(request.getFailureReason()!=null){
                        payout.setFailureReason(
                                request.getFailureReason()
                        );
                    }

                    return payoutRepository.save(payout);
                })
                .flatMap(saved->notifications.audit(
                        "PAYOUT_FAILED",
                        "PAYOUT",
                        saved.getId(),
                        null,
                        "payoutReference="+
                                saved.getPayoutReference()+
                                ",reason="+saved.getFailureReason()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyPayoutFailed(
                                saved.getCustomerId(),
                                saved.getPayoutReference(),
                                saved.getFailureReason()
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Mono<PayoutResponse> retry(
            Long payoutId,
            PayoutActionRequest request
    ){
        return SecurityUtil.requireAdmin()
                .then(findEntity(payoutId))
                .flatMap(payout->{
                    if(payout.getStatus()!=PayoutStatus.FAILED){
                        return Mono.error(
                                new ConflictException(
                                        "Only failed payouts can be retried"
                                )
                        );
                    }

                    payout.setStatus(PayoutStatus.PROCESSING);
                    payout.setRetryCount(payout.getRetryCount()+1);
                    payout.setProcessedAt(LocalDateTime.now());
                    payout.setUpdatedAt(LocalDateTime.now());

                    return payoutRepository.save(payout);
                })
                .flatMap(saved->notifications.audit(
                        "PAYOUT_RETRY",
                        "PAYOUT",
                        saved.getId(),
                        null,
                        "payoutReference="+
                                saved.getPayoutReference()+
                                ",retryCount="+saved.getRetryCount()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyPayoutRetry(
                                saved.getCustomerId(),
                                saved.getPayoutReference(),
                                saved.getAmount(),
                                saved.getRetryCount()
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    private void validateClaim(
            String status,
            BigDecimal approvedAmount,
            Long customerId,
            Long policyId
    ){
        if(!"APPROVED".equals(status)){
            throw new ConflictException(
                    "Claim is not approved"
            );
        }

        if(approvedAmount==null||
                approvedAmount.compareTo(BigDecimal.ZERO)<=0){
            throw new ValidationException(
                    "Approved amount must be greater than zero"
            );
        }

        if(customerId==null){
            throw new ValidationException(
                    "Claim has no customer"
            );
        }

        if(policyId==null){
            throw new ValidationException(
                    "Claim has no policy"
            );
        }
    }

    private Mono<Payout> findEntity(Long payoutId){
        return payoutRepository.findById(payoutId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Payout not found")
                ));
    }

    private String generateReference(){
        return "PO-"+
                UUID.randomUUID()
                        .toString()
                        .replace("-","")
                        .substring(0,16)
                        .toUpperCase();
    }

    private PayoutResponse response(Payout payout){
        return PayoutResponse.builder()
                .id(payout.getId())
                .payoutReference(payout.getPayoutReference())
                .customerId(payout.getCustomerId())
                .claimId(payout.getClaimId())
                .claimNumber(payout.getClaimNumber())
                .policyId(payout.getPolicyId())
                .triggerEventId(payout.getTriggerEventId())
                .amount(payout.getAmount())
                .status(payout.getStatus())
                .externalReference(payout.getExternalReference())
                .failureReason(payout.getFailureReason())
                .retryCount(payout.getRetryCount())
                .createdAt(payout.getCreatedAt())
                .processedAt(payout.getProcessedAt())
                .updatedAt(payout.getUpdatedAt())
                .build();
    }
}