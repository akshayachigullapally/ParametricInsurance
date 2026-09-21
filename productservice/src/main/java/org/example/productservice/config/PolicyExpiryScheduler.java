package org.example.productservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.productservice.model.Policy;
import org.example.productservice.model.PolicyStatus;
import org.example.productservice.model.PolicyStatusHistory;
import org.example.productservice.repository.PolicyRepository;
import org.example.productservice.repository.PolicyStatusHistoryRepository;
import org.example.productservice.service.NotificationService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class PolicyExpiryScheduler{
    private final PolicyRepository policyRepository;
    private final PolicyStatusHistoryRepository historyRepository;
    private final NotificationService notificationService;

    @Scheduled(cron="0 0 1 * * ?")
    public Mono<Void> expirePolicies(){
        LocalDate today=LocalDate.now();

        return policyRepository.findAllByStatus(PolicyStatus.ACTIVE.name())
                .filter(policy->policy.getEndDate()!=null&&!policy.getEndDate().isAfter(today))
                .concatMap(policy->expirePolicy(policy))
                .doOnComplete(()->log.info("Policy expiry sweep completed"))
                .doOnError(error->log.error("Policy expiry sweep failed",error))
                .then();
    }

    private Mono<Void> expirePolicy(Policy policy){
        String oldStatus=policy.getStatus();
        LocalDateTime now=LocalDateTime.now();

        policy.setStatus(PolicyStatus.EXPIRED.name());
        policy.setUpdatedAt(now);

        return policyRepository.save(policy)
                .flatMap(saved->{
                    PolicyStatusHistory history=PolicyStatusHistory.builder()
                            .policyId(saved.getId())
                            .oldStatus(oldStatus)
                            .newStatus(PolicyStatus.EXPIRED.name())
                            .changedBy(null)
                            .changeReason("Policy term expired")
                            .changedAt(now)
                            .build();

                    return historyRepository.save(history)
                            .then(notificationService.notifyPolicyExpired(
                                    saved.getCustomerId(),
                                    saved.getId(),
                                    saved.getPolicyNumber()
                            ));
                })
                .then();
    }
}
