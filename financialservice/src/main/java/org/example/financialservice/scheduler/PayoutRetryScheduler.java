package org.example.financialservice.scheduler;

import java.time.LocalDateTime;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.repository.PayoutRepository;
import org.example.financialservice.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PayoutRetryScheduler{
    private final PayoutRepository payoutRepository;
    private final NotificationService notifications;

    public PayoutRetryScheduler(
            PayoutRepository payoutRepository,
            NotificationService notifications
    ){
        this.payoutRepository=payoutRepository;
        this.notifications=notifications;
    }

    @Scheduled(cron="0 */5 * * * *")
    public void retryFailedPayouts(){
        int retried=payoutRepository
                .findAllByStatusAndRetryCountLessThan(
                        PayoutStatus.FAILED,
                        3
                )
                .flatMap(payout->{
                    payout.setStatus(PayoutStatus.PROCESSING);
                    payout.setRetryCount(payout.getRetryCount()+1);
                    payout.setProcessedAt(LocalDateTime.now());
                    payout.setUpdatedAt(LocalDateTime.now());

                    return payoutRepository.save(payout);
                })
                .flatMap(saved->notifications.audit(
                        "PAYOUT_AUTO_RETRY",
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
                .collectList()
                .block()
                .size();

        log.info("Payout retry scheduler retried {} payouts", retried);
    }
}