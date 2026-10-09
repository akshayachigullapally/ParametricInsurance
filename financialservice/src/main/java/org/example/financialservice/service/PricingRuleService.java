package org.example.financialservice.service;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.example.financialservice.dto.PricingRuleRequest;
import org.example.financialservice.dto.PricingRuleResponse;
import org.example.financialservice.dto.UpdatePricingRuleStatusRequest;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.model.PricingRule;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.repository.PricingRuleRepository;
import org.example.financialservice.util.SecurityUtil;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class PricingRuleService{
    private final PricingRuleRepository repository;
    private final ProductServiceClient productServiceClient;
    private final TriggerServiceClient triggerServiceClient;
    private final NotificationService notifications;
    private final DefaultPricingRuleProvisioner defaultRules;

    public PricingRuleService(
            PricingRuleRepository repository,
            ProductServiceClient productServiceClient,
            TriggerServiceClient triggerServiceClient,
            NotificationService notifications,
            DefaultPricingRuleProvisioner defaultRules
    ){
        this.repository=repository;
        this.productServiceClient=productServiceClient;
        this.triggerServiceClient=triggerServiceClient;
        this.notifications=notifications;
        this.defaultRules=defaultRules;
    }

    public Mono<PricingRuleResponse> create(
            PricingRuleRequest request
    ){
        log.info("Create [pricing rule]");
        validateValues(request);

        return SecurityUtil.requireAdmin()
                .then(productServiceClient.findProduct(
                        request.getProductId()
                ))
                .then(zoneCheck(request))
                .then(SecurityUtil.userId())
                .flatMap(userId->{
                    PricingRule rule=PricingRule.builder()
                            .productId(request.getProductId())
                            .baseAnnualRate(request.getBaseAnnualRate())
                            .minimumPremium(request.getMinimumPremium())
                            .termFactor(request.getTermFactor())
                            .locationZoneId(request.getLocationZoneId())
                            .locationFactor(request.getLocationFactor())
                            .status(PricingRuleStatus.DRAFT)
                            .effectiveFrom(request.getEffectiveFrom())
                            .effectiveTo(request.getEffectiveTo())
                            .createdBy(userId)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return repository.save(rule);
                })
                .flatMap(saved->notifications
                        .notifyPricingRuleCreated(
                                saved.getCreatedBy(),
                                saved.getProductId(),
                                saved.getId()
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Flux<PricingRuleResponse> findAll(){
        return SecurityUtil.requireAdmin()
                .then(defaultRules.provisionMissingRules()
                        .onErrorResume(error->{
                            log.warn(
                                    "Default pricing rule sync failed: {}",
                                    error.getMessage()
                            );
                            return Mono.empty();
                        }))
                .thenMany(repository.findAllByOrderByCreatedAtDesc())
                .map(this::response);
    }

    public Mono<PricingRuleResponse> findById(Long ruleId){
        log.debug("Find by id [pricing rule]: ruleId={}",ruleId);
        return SecurityUtil.requireAdmin()
                .then(findEntity(ruleId))
                .map(this::response);
    }

    public Mono<PricingRuleResponse> update(
            Long ruleId,
            PricingRuleRequest request
    ){
        log.info("Update [pricing rule]: ruleId={}",ruleId);
        validateValues(request);

        return SecurityUtil.requireAdmin()
                .then(SecurityUtil.userId())
                .flatMap(actor->productServiceClient
                        .findProduct(request.getProductId())
                        .then(zoneCheck(request))
                        .then(findEntity(ruleId))
                        .flatMap(rule->{
                            if(rule.getStatus()==
                                    PricingRuleStatus.ACTIVE){
                                return Mono.error(
                                        new ConflictException(
                                                "Active pricing rule cannot be edited"
                                        )
                                );
                            }

                            if(rule.getStatus()==
                                    PricingRuleStatus.ARCHIVED){
                                return Mono.error(
                                        new ConflictException(
                                                "Archived pricing rule cannot be edited"
                                        )
                                );
                            }


                            rule.setProductId(request.getProductId());
                            rule.setBaseAnnualRate(
                                    request.getBaseAnnualRate()
                            );
                            rule.setMinimumPremium(
                                    request.getMinimumPremium()
                            );
                            rule.setTermFactor(request.getTermFactor());
                            rule.setLocationZoneId(
                                    request.getLocationZoneId()
                            );
                            rule.setLocationFactor(
                                    request.getLocationFactor()
                            );
                            rule.setEffectiveFrom(
                                    request.getEffectiveFrom()
                            );
                            rule.setEffectiveTo(
                                    request.getEffectiveTo()
                            );
                            rule.setUpdatedBy(actor);
                            rule.setUpdatedAt(LocalDateTime.now());

                            return repository.save(rule);
                        })
                )
                .flatMap(saved->notifications
                        .notifyPricingRuleUpdated(
                                saved.getUpdatedBy(),
                                saved.getProductId(),
                                saved.getId()
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Mono<PricingRuleResponse> updateStatus(
            Long ruleId,
            UpdatePricingRuleStatusRequest request
    ){
        log.info("Update status [pricing rule]: ruleId={}",ruleId);
        java.util.concurrent.atomic.AtomicReference<PricingRuleStatus> oldStatus=
                new java.util.concurrent.atomic.AtomicReference<>();
        return SecurityUtil.requireAdmin()
                .then(findEntity(ruleId))
                .flatMap(rule->validateTransition(
                        rule,
                        request.getStatus()
                ))
                .flatMap(rule->request.getStatus()==
                        PricingRuleStatus.ACTIVE
                        ?activationChecks(rule).thenReturn(rule)
                        :Mono.just(rule))
                .flatMap(rule->SecurityUtil.userId()
                        .flatMap(userId->{
                            oldStatus.set(rule.getStatus());
                            rule.setStatus(request.getStatus());
                            rule.setUpdatedBy(userId);
                            rule.setUpdatedAt(LocalDateTime.now());

                            return repository.save(rule);
                        })
                )
                .flatMap(saved->saved.getStatus()==PricingRuleStatus.ACTIVE
                        ?supersedeCurrentRules(saved).thenReturn(saved)
                        :Mono.just(saved))
                .map(this::response);
    }

    /**
     * A newly activated rule that is already in effect replaces the active
     * rule(s) for the same product and zone: those become INACTIVE.
     * A future-dated rule is activated alongside the current one; quotes pick
     * the latest rule in effect, so it takes over on its start date.
     */
    private Mono<Void> supersedeCurrentRules(PricingRule activated){
        if(activated.getEffectiveFrom()!=null&&
                activated.getEffectiveFrom().isAfter(LocalDate.now())){
            return Mono.empty();
        }

        return repository
                .findAllByProductIdAndStatus(
                        activated.getProductId(),
                        PricingRuleStatus.ACTIVE
                )
                .filter(existing->!existing.getId().equals(activated.getId()))
                .filter(existing->sameZone(
                        existing.getLocationZoneId(),
                        activated.getLocationZoneId()
                ))
                .concatMap(existing->{
                    existing.setStatus(PricingRuleStatus.INACTIVE);
                    existing.setUpdatedBy(activated.getUpdatedBy());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return repository.save(existing);
                })
                .then();
    }

    public Mono<PricingRuleResponse> archive(Long ruleId){
        log.info("Archive [pricing rule]: ruleId={}",ruleId);
        return SecurityUtil.requireAdmin()
                .then(SecurityUtil.userId())
                .flatMap(actor->findEntity(ruleId)
                        .flatMap(rule->{
                            if(rule.getStatus()==
                                    PricingRuleStatus.ACTIVE){
                                return Mono.error(
                                        new ConflictException(
                                                "Deactivate pricing rule before archiving"
                                        )
                                );
                            }

                            if(rule.getStatus()==
                                    PricingRuleStatus.ARCHIVED){
                                return Mono.just(rule);
                            }

                            rule.setStatus(PricingRuleStatus.ARCHIVED);
                            rule.setUpdatedBy(actor);
                            rule.setUpdatedAt(LocalDateTime.now());

                            return repository.save(rule);
                        })
                )
                .map(this::response);
    }

    private Mono<Void> zoneCheck(PricingRuleRequest request){
        if(request.getLocationZoneId()==null){
            return Mono.empty();
        }

        return triggerServiceClient.requireActiveZone(
                request.getLocationZoneId()
        );
    }

    private Mono<PricingRule> validateTransition(
            PricingRule rule,
            PricingRuleStatus target
    ){
        if(rule.getStatus()==target){
            return Mono.just(rule);
        }

        // An archived rule can only be restored, and it comes back INACTIVE.
        if(rule.getStatus()==PricingRuleStatus.ARCHIVED&&
                target!=PricingRuleStatus.INACTIVE){
            return Mono.error(
                    new ConflictException(
                            "Restore the archived pricing rule (to INACTIVE) before changing its status"
                    )
            );
        }

        if(target==PricingRuleStatus.ARCHIVED&&
                rule.getStatus()==PricingRuleStatus.ACTIVE){
            return Mono.error(
                    new ConflictException(
                            "Deactivate pricing rule before archiving"
                    )
            );
        }

        if(target==PricingRuleStatus.ACTIVE){
            LocalDate today=LocalDate.now();

            if(rule.getEffectiveTo()!=null&&
                    rule.getEffectiveTo().isBefore(today)){
                return Mono.error(
                        new ValidationException(
                                "Expired pricing rule cannot be activated"
                        )
                );
            }
        }

        return Mono.just(rule);
    }

    private Mono<Void> activationChecks(PricingRule rule){
        Mono<Void> productCheck=productServiceClient
                .requireActiveProduct(rule.getProductId());

        Mono<Void> zoneCheck=rule.getLocationZoneId()!=null
                ?triggerServiceClient.requireActiveZone(
                rule.getLocationZoneId()
        )
                :Mono.empty();

        return Mono.when(
                productCheck,
                zoneCheck
        );
    }

    private boolean sameZone(Long first,Long second){
        if(first==null&&second==null){
            return true;
        }

        return first!=null&&first.equals(second);
    }

    private void validateValues(PricingRuleRequest request){
        if(request.getBaseAnnualRate().compareTo(BigDecimal.ZERO)<=0||
                request.getBaseAnnualRate()
                        .compareTo(BigDecimal.ONE)>0){
            throw new ValidationException(
                    "baseAnnualRate must be greater than 0 and at most 1"
            );
        }

        if(request.getMinimumPremium()
                .compareTo(BigDecimal.ZERO)<0){
            throw new ValidationException(
                    "minimumPremium cannot be negative"
            );
        }

        if(request.getTermFactor().compareTo(BigDecimal.ZERO)<=0||
                request.getTermFactor().compareTo(BigDecimal.TEN)>0){
            throw new ValidationException(
                    "termFactor must be greater than 0 and at most 10"
            );
        }

        if(request.getLocationFactor().compareTo(BigDecimal.ZERO)<=0||
                request.getLocationFactor()
                        .compareTo(BigDecimal.TEN)>0){
            throw new ValidationException(
                    "locationFactor must be greater than 0 and at most 10"
            );
        }

        validateDates(request);
    }

    private void validateDates(PricingRuleRequest request){
        if(request.getEffectiveTo()!=null&&
                request.getEffectiveTo()
                        .isBefore(request.getEffectiveFrom())){
            throw new ValidationException(
                    "Effective end date cannot precede start date"
            );
        }
    }

    private Mono<PricingRule> findEntity(Long ruleId){
        return repository.findById(ruleId)
                .switchIfEmpty(
                        Mono.error(
                                new NotFoundException(
                                        "Pricing rule not found"
                                )
                        )
                );
    }

    private PricingRuleResponse response(PricingRule rule){
        return PricingRuleResponse.builder()
                .id(rule.getId())
                .productId(rule.getProductId())
                .baseAnnualRate(rule.getBaseAnnualRate())
                .minimumPremium(rule.getMinimumPremium())
                .termFactor(rule.getTermFactor())
                .locationZoneId(rule.getLocationZoneId())
                .locationFactor(rule.getLocationFactor())
                .status(rule.getStatus())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .createdBy(rule.getCreatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedBy(rule.getUpdatedBy())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

}
