package org.example.financialservice.scheduler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.financialservice.model.Payment;
import org.example.financialservice.model.PaymentStatus;
import org.example.financialservice.model.Payout;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.model.ReconciliationRecord;
import org.example.financialservice.repository.FinancialTransactionRepository;
import org.example.financialservice.repository.PaymentRepository;
import org.example.financialservice.repository.PayoutRepository;
import org.example.financialservice.repository.ReconciliationRecordRepository;
import org.example.financialservice.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ReconciliationScheduler{
    private final PaymentRepository paymentRepository;
    private final PayoutRepository payoutRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final ReconciliationRecordRepository recordRepository;
    private final NotificationService notifications;

    public ReconciliationScheduler(
            PaymentRepository paymentRepository,
            PayoutRepository payoutRepository,
            FinancialTransactionRepository transactionRepository,
            ReconciliationRecordRepository recordRepository,
            NotificationService notifications
    ){
        this.paymentRepository=paymentRepository;
        this.payoutRepository=payoutRepository;
        this.transactionRepository=transactionRepository;
        this.recordRepository=recordRepository;
        this.notifications=notifications;
    }

    @Scheduled(cron="0 30 3 * * *")
    public void runReconciliation(){
        String runReference=generateReference();
        AtomicInteger reconciled=new AtomicInteger();
        AtomicInteger mismatches=new AtomicInteger();

        Mono<Void> paymentPass=paymentRepository
                .findAllByStatusIn(List.of(
                        PaymentStatus.SUCCESS,
                        PaymentStatus.REFUNDED
                ))
                .concatMap(payment->reconcilePayment(
                        payment,
                        reconciled,
                        mismatches
                ))
                .then();

        Mono<Void> payoutPass=payoutRepository
                .findAllByStatus(PayoutStatus.SUCCESS)
                .concatMap(payout->reconcilePayout(
                        payout,
                        reconciled,
                        mismatches
                ))
                .then();

        paymentPass.and(payoutPass)
                .doOnSuccess(v->summarize(
                        runReference,
                        reconciled.get(),
                        mismatches.get()
                ))
                .block();
    }

    private Mono<Void> reconcilePayment(
            Payment payment,
            AtomicInteger reconciled,
            AtomicInteger mismatches
    ){
        return recordRepository
                .existsByEntityIdAndTransactionType(
                        payment.getId(),
                        "PAYMENT"
                )
                .flatMap(exists->{
                    if(exists){
                        return Mono.empty();
                    }

                    return transactionRepository
                            .findAllByPaymentIdOrderByCreatedAtDesc(
                                    payment.getId()
                            )
                            .hasElements()
                            .flatMap(matched->saveRecord(
                                    "PAYMENT",
                                    payment.getId(),
                                    payment.getPaymentReference(),
                                    payment.getAmount(),
                                    matched,
                                    reconciled,
                                    mismatches
                            ));
                });
    }

    private Mono<Void> reconcilePayout(
            Payout payout,
            AtomicInteger reconciled,
            AtomicInteger mismatches
    ){
        return recordRepository
                .existsByEntityIdAndTransactionType(
                        payout.getId(),
                        "PAYOUT"
                )
                .flatMap(exists->{
                    if(exists){
                        return Mono.empty();
                    }

                    return transactionRepository
                            .findAllByPayoutIdOrderByCreatedAtDesc(
                                    payout.getId()
                            )
                            .hasElements()
                            .flatMap(matched->saveRecord(
                                    "PAYOUT",
                                    payout.getId(),
                                    payout.getPayoutReference(),
                                    payout.getAmount(),
                                    matched,
                                    reconciled,
                                    mismatches
                            ));
                });
    }

    private Mono<Void> saveRecord(
            String transactionType,
            Long entityId,
            String entityReference,
            BigDecimal amount,
            boolean matched,
            AtomicInteger reconciled,
            AtomicInteger mismatches
    ){
        reconciled.incrementAndGet();

        if(!matched){
            mismatches.incrementAndGet();
        }

        return recordRepository.save(ReconciliationRecord.builder()
                        .reconciliationReference(generateReference())
                        .transactionType(transactionType)
                        .entityId(entityId)
                        .entityReference(entityReference)
                        .amount(amount)
                        .status(matched?"MATCHED":"MISMATCH")
                        .notes(matched
                                ?"Reconciled successfully"
                                :"Financial transaction record missing")
                        .createdAt(LocalDateTime.now())
                        .reconciledAt(LocalDateTime.now())
                        .build())
                .then();
    }

    private void summarize(
            String runReference,
            int reconciledCount,
            int mismatchCount
    ){
        log.info(
                "Reconciliation {} processed {} records with {} mismatches",
                runReference,
                reconciledCount,
                mismatchCount
        );

        if(mismatchCount>0){
            notifications
                    .notifyReconciliationDiscrepancy(
                            runReference,
                            mismatchCount
                    )
                    .block();
        }else if(reconciledCount>0){
            notifications
                    .notifyReconciliationCompleted(runReference)
                    .block();
        }
    }

    private String generateReference(){
        return "RCON-"+
                UUID.randomUUID()
                        .toString()
                        .replace("-","")
                        .substring(0,16)
                        .toUpperCase();
    }
}