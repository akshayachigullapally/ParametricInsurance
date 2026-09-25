package org.example.financialservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.example.financialservice.dto.InternalPolicyResponse;
import org.example.financialservice.dto.PaymentActionRequest;
import org.example.financialservice.dto.PaymentRequest;
import org.example.financialservice.dto.PaymentResponse;
import org.example.financialservice.dto.PaymentStatusResult;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.ForbiddenException;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.model.Payment;
import org.example.financialservice.model.PaymentStatus;
import org.example.financialservice.model.QuoteStatus;
import org.example.financialservice.repository.PaymentRepository;
import org.example.financialservice.repository.QuoteRepository;
import org.example.financialservice.util.SecurityUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PaymentService{
    private final PaymentRepository paymentRepository;
    private final QuoteRepository quoteRepository;
    private final ProductServiceClient productServiceClient;
    private final NotificationService notifications;
    private final FinancialTransactionService financialTransactions;

    public PaymentService(
            PaymentRepository paymentRepository,
            QuoteRepository quoteRepository,
            ProductServiceClient productServiceClient,
            NotificationService notifications,
            FinancialTransactionService financialTransactions
    ){
        this.paymentRepository=paymentRepository;
        this.quoteRepository=quoteRepository;
        this.productServiceClient=productServiceClient;
        this.notifications=notifications;
        this.financialTransactions=financialTransactions;
    }

    public Mono<PaymentResponse> create(PaymentRequest request){
        return SecurityUtil.requireCustomer()
                .flatMap(customerId->productServiceClient
                        .findPolicy(request.getPolicyId())
                        .flatMap(policy->{
                            if(!policy.getCustomerId()
                                    .equals(customerId)){
                                return Mono.error(
                                        new ForbiddenException(
                                                "Customer does not own policy"
                                        )
                                );
                            }

                            // A quote is optional: policies applied for directly
                            // from a product carry no quote and are paid at the
                            // policy premium once the underwriter activates them.
                            return quoteRepository
                                    .findByPolicyIdAndStatus(
                                            policy.getId(),
                                            QuoteStatus.CONSUMED
                                    )
                                    .map(quote->Optional.of(quote.getId()))
                                    .switchIfEmpty(Mono.defer(()->
                                            requirePayableWithoutQuote(policy)
                                                    .thenReturn(Optional.<Long>empty())
                                    ))
                                    .flatMap(quoteId->paymentRepository
                                            .existsByPolicyIdAndStatus(
                                                    policy.getId(),
                                                    PaymentStatus.SUCCESS
                                            )
                                            .flatMap(exists->exists
                                                    ?Mono.error(
                                                    new ConflictException(
                                                            "Policy already paid successfully"
                                                    )
                                            )
                                                    :Mono.empty()
                                            )
                                            .then(paymentRepository
                                                    .countByPolicyId(
                                                            policy.getId()
                                                    )
                                            )
                                            .flatMap(count->{
                                                Payment payment=
                                                        Payment.builder()
                                                                .paymentReference(
                                                                        generateReference()
                                                                )
                                                                .customerId(
                                                                        customerId
                                                                )
                                                                .policyId(
                                                                        policy.getId()
                                                                )
                                                                .policyNumber(
                                                                        policy.getPolicyNumber()
                                                                )
                                                                .quoteId(
                                                                        quoteId.orElse(null)
                                                                )
                                                                .amount(
                                                                        policy.getPremiumAmount()
                                                                )
                                                                .method(
                                                                        request.getMethod()
                                                                )
                                                                .status(
                                                                        PaymentStatus.PENDING
                                                                )
                                                                .attemptNumber(
                                                                        count.intValue()+1
                                                                )
                                                                .createdAt(
                                                                        LocalDateTime.now()
                                                                )
                                                                .build();

                                                return paymentRepository
                                                        .save(payment);
                                            })
                                    );
                        })
                )
                .flatMap(saved->notifications.audit(
                        "PAYMENT_CREATED",
                        "PAYMENT",
                        saved.getId(),
                        saved.getCustomerId(),
                        "paymentReference="+
                                saved.getPaymentReference()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyPaymentPending(
                                saved.getCustomerId(),
                                saved.getPaymentReference(),
                                saved.getAmount()
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Mono<PaymentResponse> findById(Long paymentId){
        return SecurityUtil.requireCustomer()
                .flatMap(customerId->paymentRepository
                        .findByIdAndCustomerId(
                                paymentId,
                                customerId
                        )
                )
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Payment not found")
                ))
                .map(this::response);
    }

    public Flux<PaymentResponse> findMine(){
        return SecurityUtil.requireCustomer()
                .flatMapMany(customerId->paymentRepository
                        .findAllByCustomerIdOrderByCreatedAtDesc(
                                customerId
                        )
                )
                .map(this::response);
    }

    public Flux<PaymentResponse> findAll(){
        return SecurityUtil.requireAdmin()
                .thenMany(paymentRepository
                        .findAllByOrderByCreatedAtDesc()
                )
                .map(this::response);
    }

    public Mono<PaymentResponse> success(
            Long paymentId,
            PaymentActionRequest request
    ){
        return SecurityUtil.requireAdmin()
                .then(findEntity(paymentId))
                .flatMap(payment->{
                    if(payment.getStatus()!=PaymentStatus.PENDING){
                        return Mono.error(
                                new ConflictException(
                                        "Only pending payments can succeed"
                                )
                        );
                    }

                    payment.setStatus(PaymentStatus.SUCCESS);
                    payment.setCompletedAt(LocalDateTime.now());
                    payment.setUpdatedAt(LocalDateTime.now());

                    if(request.getExternalReference()!=null){
                        payment.setExternalReference(
                                request.getExternalReference()
                        );
                    }

                    return paymentRepository.save(payment);
                })
                .flatMap(saved->notifications.audit(
                        "PAYMENT_SUCCESS",
                        "PAYMENT",
                        saved.getId(),
                        null,
                        "paymentReference="+
                                saved.getPaymentReference()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyPaymentSuccess(
                                saved.getCustomerId(),
                                saved.getPaymentReference(),
                                saved.getAmount()
                        )
                        .thenReturn(saved))
                .flatMap(saved->financialTransactions
                        .recordPayment(
                                saved.getCustomerId(),
                                saved.getPolicyId(),
                                saved.getQuoteId(),
                                saved.getId(),
                                saved.getAmount(),
                                "SUCCESS"
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Mono<PaymentResponse> fail(
            Long paymentId,
            PaymentActionRequest request
    ){
        return SecurityUtil.requireAdmin()
                .then(findEntity(paymentId))
                .flatMap(payment->{
                    if(payment.getStatus()!=PaymentStatus.PENDING){
                        return Mono.error(
                                new ConflictException(
                                        "Only pending payments can fail"
                                )
                        );
                    }

                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setCompletedAt(LocalDateTime.now());
                    payment.setUpdatedAt(LocalDateTime.now());

                    if(request.getFailureReason()!=null){
                        payment.setFailureReason(
                                request.getFailureReason()
                        );
                    }

                    return paymentRepository.save(payment);
                })
                .flatMap(saved->notifications.audit(
                        "PAYMENT_FAILED",
                        "PAYMENT",
                        saved.getId(),
                        null,
                        "paymentReference="+
                                saved.getPaymentReference()+
                                ",reason="+saved.getFailureReason()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyPaymentFailed(
                                saved.getCustomerId(),
                                saved.getPaymentReference(),
                                saved.getFailureReason()
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Mono<PaymentResponse> refund(Long paymentId){
        return SecurityUtil.requireAdmin()
                .then(findEntity(paymentId))
                .flatMap(payment->{
                    if(payment.getStatus()!=PaymentStatus.SUCCESS){
                        return Mono.error(
                                new ConflictException(
                                        "Only successful payments can be refunded"
                                )
                        );
                    }

                    payment.setStatus(PaymentStatus.REFUNDED);
                    payment.setUpdatedAt(LocalDateTime.now());

                    return paymentRepository.save(payment);
                })
                .flatMap(saved->notifications.audit(
                        "PAYMENT_REFUNDED",
                        "PAYMENT",
                        saved.getId(),
                        null,
                        "paymentReference="+
                                saved.getPaymentReference()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyPaymentRefunded(
                                saved.getCustomerId(),
                                saved.getPaymentReference(),
                                saved.getAmount()
                        )
                        .thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyRefundProcessed(
                                saved.getCustomerId(),
                                saved.getPaymentReference(),
                                saved.getAmount()
                        )
                        .thenReturn(saved))
                .flatMap(saved->financialTransactions
                        .recordRefund(
                                saved.getCustomerId(),
                                saved.getPolicyId(),
                                saved.getId(),
                                saved.getAmount()
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Mono<PaymentResponse> retry(Long paymentId){
        return SecurityUtil.requireAdmin()
                .then(findEntity(paymentId))
                .flatMap(payment->{
                    if(payment.getStatus()!=PaymentStatus.FAILED){
                        return Mono.error(
                                new ConflictException(
                                        "Only failed payments can be retried"
                                )
                        );
                    }

                    payment.setStatus(PaymentStatus.PENDING);
                    payment.setAttemptNumber(payment.getAttemptNumber()+1);
                    payment.setFailureReason(null);
                    payment.setUpdatedAt(LocalDateTime.now());

                    return paymentRepository.save(payment);
                })
                .flatMap(saved->notifications.audit(
                        "PAYMENT_RETRY",
                        "PAYMENT",
                        saved.getId(),
                        null,
                        "paymentReference="+
                                saved.getPaymentReference()+
                                ",attempt="+saved.getAttemptNumber()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyPaymentRetry(
                                saved.getCustomerId(),
                                saved.getPaymentReference(),
                                saved.getAmount(),
                                saved.getAttemptNumber()
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Mono<PaymentStatusResult> policyStatus(Long policyId){
        return paymentRepository
                .findAllByPolicyIdOrderByCreatedAtDesc(policyId)
                .next()
                .map(payment->PaymentStatusResult.builder()
                        .policyId(policyId)
                        .latestStatus(payment.getStatus())
                        .paymentReference(
                                payment.getPaymentReference()
                        )
                        .successful(
                                payment.getStatus()==
                                        PaymentStatus.SUCCESS
                        )
                        .build())
                .switchIfEmpty(Mono.just(
                        PaymentStatusResult.builder()
                                .policyId(policyId)
                                .successful(false)
                                .latestStatus(null)
                                .paymentReference(null)
                                .build()
                ));
    }

    private Mono<Payment> findEntity(Long paymentId){
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException(
                                "Payment not found"
                        )
                ));
    }

    private Mono<Void> requirePayableWithoutQuote(
            InternalPolicyResponse policy
    ){
        if(!"ACTIVE".equals(policy.getStatus())){
            return Mono.error(new ConflictException(
                    "Policy must be active before payment"
            ));
        }

        if(policy.getPremiumAmount()==null||
                policy.getPremiumAmount().signum()<=0){
            return Mono.error(new ConflictException(
                    "Policy has no premium to pay"
            ));
        }

        return Mono.empty();
    }

    private String generateReference(){
        return "PAY-"+
                UUID.randomUUID()
                        .toString()
                        .replace("-","")
                        .substring(0,16)
                        .toUpperCase();
    }

    private PaymentResponse response(Payment payment){
        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentReference(payment.getPaymentReference())
                .customerId(payment.getCustomerId())
                .policyId(payment.getPolicyId())
                .policyNumber(payment.getPolicyNumber())
                .quoteId(payment.getQuoteId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .externalReference(payment.getExternalReference())
                .failureReason(payment.getFailureReason())
                .attemptNumber(payment.getAttemptNumber())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}