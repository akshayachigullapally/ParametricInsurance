package org.example.productservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.productservice.model.Policy;
import org.example.productservice.model.PolicyStatus;
import org.example.productservice.repository.PolicyRepository;
import org.example.productservice.service.NotificationService;
import org.example.productservice.service.PolicyService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Daily renewal sweep over active policies:
 * - auto-renew on: create the renewal application 14 days before the end date;
 * - auto-renew off: remind the customer 14 and 3 days before the end date.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyRenewalScheduler{
    static final int AUTO_RENEW_LEAD_DAYS=14;
    static final int REMINDER_DAYS_EARLY=14;
    static final int REMINDER_DAYS_LATE=3;

    private final PolicyRepository policyRepository;
    private final PolicyService policyService;
    private final NotificationService notificationService;

    @Scheduled(cron="0 30 1 * * ?")
    public void runRenewalSweep(){
        sweep(LocalDate.now()).block();
    }

    Mono<Void> sweep(LocalDate today){
        return policyRepository.findAllByStatus(PolicyStatus.ACTIVE.name())
                .filter(policy->policy.getEndDate()!=null&&!policy.getEndDate().isBefore(today))
                .concatMap(policy->handle(policy,ChronoUnit.DAYS.between(today,policy.getEndDate())))
                .doOnComplete(()->log.info("Policy renewal sweep completed"))
                .doOnError(error->log.error("Policy renewal sweep failed",error))
                .onErrorComplete()
                .then();
    }

    private Mono<Void> handle(Policy policy,long daysLeft){
        if(Boolean.TRUE.equals(policy.getAutoRenew())&&daysLeft<=AUTO_RENEW_LEAD_DAYS){
            return policyService.createAutomaticRenewal(policy)
                    .doOnNext(renewal->log.info("Auto-renewal {} created for policy {}",
                            renewal.getPolicyNumber(),policy.getPolicyNumber()))
                    .onErrorResume(error->{
                        // Already renewed / pending application: nothing to do.
                        log.debug("Auto-renewal skipped for {}: {}",policy.getPolicyNumber(),error.getMessage());
                        return Mono.empty();
                    })
                    .then();
        }
        if(daysLeft==REMINDER_DAYS_EARLY||daysLeft==REMINDER_DAYS_LATE){
            return notificationService.notifyPolicyRenewalDue(
                    policy.getCustomerId(),policy.getId(),policy.getPolicyNumber());
        }
        return Mono.empty();
    }
}
