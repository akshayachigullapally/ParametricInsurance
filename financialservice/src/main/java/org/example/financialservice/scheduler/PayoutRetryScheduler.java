package org.example.financialservice.scheduler;

import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.repository.PayoutRepository;
import org.example.financialservice.service.PayoutService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Payouts are paid the moment a claim is approved. This sweep is the safety net: it pays any
 * payout still waiting (e.g. created before payouts were automatic, or interrupted by a
 * restart) and retries failed ones up to three times.
 */
@Slf4j
@Component
public class PayoutRetryScheduler{
    static final int MAX_RETRIES=3;

    private final PayoutRepository payoutRepository;
    private final PayoutService payoutService;

    public PayoutRetryScheduler(
            PayoutRepository payoutRepository,
            PayoutService payoutService
    ){
        this.payoutRepository=payoutRepository;
        this.payoutService=payoutService;
    }

    @Scheduled(initialDelayString="${payout.sweep.initial-delay-ms:30000}",
            fixedDelayString="${payout.sweep.interval-ms:300000}")
    public void payOutstandingPayouts(){
        Long waiting=Flux.concat(
                        payoutRepository.findAllByStatus(PayoutStatus.PENDING),
                        payoutRepository.findAllByStatus(PayoutStatus.PROCESSING))
                .concatMap(payout->payoutService.disburse(payout)
                        .onErrorResume(error->{
                            log.warn("Could not pay payout {}: {}",
                                    payout.getPayoutReference(),error.getMessage());
                            return reactor.core.publisher.Mono.empty();
                        }))
                .count()
                .block();

        Long retried=payoutRepository
                .findAllByStatusAndRetryCountLessThan(PayoutStatus.FAILED,MAX_RETRIES)
                .concatMap(payout->payoutService.retryAutomatically(payout)
                        .onErrorResume(error->{
                            log.warn("Could not retry payout {}: {}",
                                    payout.getPayoutReference(),error.getMessage());
                            return reactor.core.publisher.Mono.empty();
                        }))
                .count()
                .block();

        if((waiting!=null&&waiting>0)||(retried!=null&&retried>0)){
            log.info("Payout sweep paid {} waiting and retried {} failed payout(s)",waiting,retried);
        }
    }
}
