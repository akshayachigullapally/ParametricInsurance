package org.example.financialservice.service;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

                    log.info("Creating payout for claim {} amount={}",
                            claim.getClaimNumber(),claim.getApprovedAmount());
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
                .flatMap(this::disburse)
                .map(this::response);
    }

    public Mono<PayoutResponse> findById(Long payoutId){
        log.debug("Find by id [payout]: payoutId={}",payoutId);
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
        log.debug("Find mine [payout]");
        return SecurityUtil.requireCustomer()
                .flatMapMany(customerId->payoutRepository
                        .findAllByCustomerIdOrderByCreatedAtDesc(
                                customerId
                        )
                )
                .map(this::response);
    }

    public Flux<PayoutResponse> findAll(){
        log.debug("Find all [payout]");
        return SecurityUtil.requireAdmin()
                .thenMany(payoutRepository
                        .findAllByOrderByCreatedAtDesc()
                )
                .map(this::response);
    }

    public Flux<PayoutResponse> findByClaim(Long claimId){
        log.debug("Find by claim [payout]: claimId={}",claimId);
        return payoutRepository
                .findAllByClaimIdOrderByCreatedAtDesc(claimId)
                .map(this::response);
    }

    /**
     * Admin retry of a failed payout: the transfer is attempted again straight away.
     */
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
                    return retryAutomatically(payout);
                })
                .map(this::response);
    }

    /** Retries a failed payout (used by the admin retry and the retry scheduler). */
    public Mono<Payout> retryAutomatically(Payout payout){
        payout.setRetryCount(payout.getRetryCount()+1);
        log.info("Retrying payout {} (retry {})",
                payout.getPayoutReference(),payout.getRetryCount());
        return notifications.audit(
                        "PAYOUT_RETRY",
                        "PAYOUT",
                        payout.getId(),
                        null,
                        "payoutReference="+payout.getPayoutReference()+
                                ",retryCount="+payout.getRetryCount())
                .then(disburse(payout));
    }

    /**
     * Pays the approved amount out straight away — there is no admin approval step. The
     * transfer is a mock bank transfer, so it completes immediately; afterwards the customer
     * is notified, the claim is settled and the trigger event is closed. Those follow-up
     * steps are best effort: a failure there is logged and never undoes the payment.
     */
    public Mono<Payout> disburse(Payout payout){
        LocalDateTime now=LocalDateTime.now();
        payout.setStatus(PayoutStatus.SUCCESS);
        payout.setFailureReason(null);
        payout.setExternalReference(generateTransferReference());
        payout.setProcessedAt(now);
        payout.setUpdatedAt(now);

        log.info("Payout {} paid automatically, transfer {}",
                payout.getPayoutReference(),payout.getExternalReference());

        return payoutRepository.save(payout)
                .flatMap(saved->notifications.audit(
                        "PAYOUT_SUCCESS",
                        "PAYOUT",
                        saved.getId(),
                        null,
                        "payoutReference="+saved.getPayoutReference()+
                                ",transfer="+saved.getExternalReference()+
                                " (automatic)"
                ).thenReturn(saved))
                .flatMap(saved->bestEffort(
                        notifications.notifyPayoutProcessed(
                                saved.getCustomerId(),
                                saved.getPayoutReference(),
                                saved.getAmount()),
                        "notify customer",saved))
                .flatMap(saved->bestEffort(
                        claimsServiceClient.settleClaim(
                                saved.getClaimId(),saved.getPayoutReference()),
                        "settle claim",saved))
                .flatMap(saved->saved.getTriggerEventId()==null
                        ?Mono.just(saved)
                        :bestEffort(
                                triggerServiceClient.markEventProcessed(
                                        saved.getTriggerEventId()),
                                "close trigger event",saved))
                .flatMap(saved->bestEffort(
                        financialTransactions.recordPayout(
                                saved.getCustomerId(),
                                saved.getPolicyId(),
                                saved.getId(),
                                saved.getClaimId(),
                                saved.getAmount(),
                                "SUCCESS").then(),
                        "record transaction",saved));
    }

    private Mono<Payout> bestEffort(Mono<?> step,String what,Payout payout){
        return step
                .then(Mono.just(payout))
                .onErrorResume(error->{
                    log.warn("Payout {}: could not {}: {}",
                            payout.getPayoutReference(),what,error.getMessage());
                    return Mono.just(payout);
                });
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

    private String generateTransferReference(){
        return "AUTO-"+
                UUID.randomUUID()
                        .toString()
                        .replace("-","")
                        .substring(0,12)
                        .toUpperCase();
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