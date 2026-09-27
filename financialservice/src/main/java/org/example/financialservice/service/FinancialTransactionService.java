package org.example.financialservice.service;

import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.example.financialservice.model.FinancialTransaction;
import org.example.financialservice.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FinancialTransactionService{
    private final FinancialTransactionRepository repository;

    public FinancialTransactionService(
            FinancialTransactionRepository repository
    ){
        this.repository=repository;
    }

    public Mono<FinancialTransaction> recordPayment(
            Long customerId,
            Long policyId,
            Long quoteId,
            Long paymentId,
            BigDecimal amount,
            String status
    ){
        log.info("Record payment [financial transaction]: customerId={}, policyId={}, quoteId={}, paymentId={}, amount={}, status={}",customerId,policyId,quoteId,paymentId,amount,status);
        FinancialTransaction transaction=FinancialTransaction.builder()
                .transactionReference(generateReference("TXN"))
                .customerId(customerId)
                .policyId(policyId)
                .quoteId(quoteId)
                .paymentId(paymentId)
                .transactionType("PREMIUM_PAYMENT")
                .amount(amount)
                .status(status)
                .description("Premium payment for policy")
                .createdAt(LocalDateTime.now())
                .build();

        return repository.save(transaction);
    }

    public Mono<FinancialTransaction> recordRefund(
            Long customerId,
            Long policyId,
            Long paymentId,
            BigDecimal amount
    ){
        log.info("Record refund [financial transaction]: customerId={}, policyId={}, paymentId={}, amount={}",customerId,policyId,paymentId,amount);
        FinancialTransaction transaction=FinancialTransaction.builder()
                .transactionReference(generateReference("TXN"))
                .customerId(customerId)
                .policyId(policyId)
                .paymentId(paymentId)
                .transactionType("REFUND")
                .amount(amount.negate())
                .status("SUCCESS")
                .description("Payment refund")
                .createdAt(LocalDateTime.now())
                .build();

        return repository.save(transaction);
    }

    public Mono<FinancialTransaction> recordPayout(
            Long customerId,
            Long policyId,
            Long payoutId,
            Long claimId,
            BigDecimal amount,
            String status
    ){
        log.info("Record payout [financial transaction]: customerId={}, policyId={}, payoutId={}, claimId={}, amount={}, status={}",customerId,policyId,payoutId,claimId,amount,status);
        FinancialTransaction transaction=FinancialTransaction.builder()
                .transactionReference(generateReference("TXN"))
                .customerId(customerId)
                .policyId(policyId)
                .payoutId(payoutId)
                .transactionType("CLAIM_PAYOUT")
                .amount(amount)
                .status(status)
                .description("Claim payout")
                .createdAt(LocalDateTime.now())
                .build();

        return repository.save(transaction);
    }

    private String generateReference(String prefix){
        return prefix+"-"+
                UUID.randomUUID()
                        .toString()
                        .replace("-","")
                        .substring(0,16)
                        .toUpperCase();
    }
}
