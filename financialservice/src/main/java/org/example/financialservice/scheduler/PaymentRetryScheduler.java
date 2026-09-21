package org.example.financialservice.scheduler;

import java.time.LocalDateTime;
import org.example.financialservice.model.PaymentStatus;
import org.example.financialservice.repository.PaymentRepository;
import org.example.financialservice.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PaymentRetryScheduler{
    private final PaymentRepository paymentRepository;
    private final NotificationService notifications;

    public PaymentRetryScheduler(
            PaymentRepository paymentRepository,
            NotificationService notifications
    ){
        this.paymentRepository=paymentRepository;
        this.notifications=notifications;
    }

    @Scheduled(cron="0 */5 * * * *")
    public void retryFailedPayments(){
        int retried=paymentRepository
                .findAllByStatusAndAttemptNumberLessThan(
                        PaymentStatus.FAILED,
                        3
                )
                .flatMap(payment->{
                    payment.setStatus(PaymentStatus.PENDING);
                    payment.setAttemptNumber(
                            payment.getAttemptNumber()+1
                    );
                    payment.setFailureReason(null);
                    payment.setUpdatedAt(LocalDateTime.now());

                    return paymentRepository.save(payment);
                })
                .flatMap(saved->notifications.audit(
                        "PAYMENT_AUTO_RETRY",
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
                .collectList()
                .block()
                .size();

        log.info("Payment retry scheduler retried {} payments", retried);
    }
}