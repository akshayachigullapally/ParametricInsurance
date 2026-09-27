package org.example.financialservice.scheduler;

import org.example.financialservice.service.DefaultPricingRuleProvisioner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DefaultPricingRuleScheduler{
    private final DefaultPricingRuleProvisioner provisioner;

    public DefaultPricingRuleScheduler(
            DefaultPricingRuleProvisioner provisioner
    ){
        this.provisioner=provisioner;
    }

    @Scheduled(
            initialDelayString="${financial.pricing.default-rule-initial-delay-ms:20000}",
            fixedDelayString="${financial.pricing.default-rule-sync-ms:60000}"
    )
    public void syncDefaultRules(){
        provisioner.provisionMissingRules()
                .doOnError(error->log.warn(
                        "Default pricing rule sync failed: {}",
                        error.getMessage()
                ))
                .onErrorComplete()
                .block();
    }
}
