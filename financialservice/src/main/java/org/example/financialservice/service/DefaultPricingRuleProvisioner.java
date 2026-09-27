package org.example.financialservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.example.financialservice.dto.InternalProductResponse;
import org.example.financialservice.model.PricingRule;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.repository.PricingRuleRepository;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Makes sure every product has a pricing rule, however the product was
 * created (admin UI, customer-defined, seed loader, direct REST/SOAP UI
 * calls or rows inserted straight into the product database).
 *
 * Products without any rule get a default zone-wide rule based on their
 * category. The rule is ACTIVE when the product is ACTIVE, otherwise DRAFT;
 * a system-created DRAFT rule is activated once its product becomes ACTIVE.
 * Rules created or changed by an admin are never touched.
 */
@Slf4j
@Service
public class DefaultPricingRuleProvisioner{
    public static final Long SYSTEM_USER_ID=0L;

    private static final BigDecimal DEFAULT_RATE=new BigDecimal("0.035000");
    private static final BigDecimal DEFAULT_MINIMUM_PREMIUM=new BigDecimal("250.00");
    private static final BigDecimal DEFAULT_FACTOR=new BigDecimal("1.0000");

    private static final Map<String,BigDecimal> CATEGORY_RATES=Map.of(
            "WEATHER",new BigDecimal("0.035000"),
            "FLOOD",new BigDecimal("0.045000"),
            "CYCLONE",new BigDecimal("0.050000"),
            "AGRICULTURE",new BigDecimal("0.040000"),
            "BUSINESS_INTERRUPTION",new BigDecimal("0.030000"),
            "TRAVEL",new BigDecimal("0.020000"),
            "OTHER",DEFAULT_RATE
    );

    private final PricingRuleRepository repository;
    private final ProductServiceClient productServiceClient;
    private final NotificationService notifications;
    private final AtomicBoolean running=new AtomicBoolean(false);

    public DefaultPricingRuleProvisioner(
            PricingRuleRepository repository,
            ProductServiceClient productServiceClient,
            NotificationService notifications
    ){
        this.repository=repository;
        this.productServiceClient=productServiceClient;
        this.notifications=notifications;
    }

    public Mono<Void> provisionMissingRules(){
        log.info("Provision missing rules [default pricing rule provisioner]");
        return Mono.defer(()->{
            if(!running.compareAndSet(false,true)){
                return Mono.empty();
            }

            return productServiceClient.findAllProducts()
                    .filter(product->product.getProductId()!=null)
                    .filter(product->!"ARCHIVED".equals(product.getStatus()))
                    .concatMap(this::provision)
                    .then()
                    .doFinally(signal->running.set(false));
        });
    }

    private Mono<Void> provision(InternalProductResponse product){
        return repository
                .findAllByProductIdOrderByCreatedAtDesc(product.getProductId())
                .collectList()
                .flatMap(rules->{
                    if(rules.isEmpty()){
                        return createDefaultRule(product);
                    }

                    if(isActive(product)){
                        return activateSystemDraft(rules);
                    }

                    return Mono.empty();
                });
    }

    private Mono<Void> createDefaultRule(InternalProductResponse product){
        PricingRule rule=PricingRule.builder()
                .productId(product.getProductId())
                .baseAnnualRate(rateFor(product.getCategory()))
                .minimumPremium(DEFAULT_MINIMUM_PREMIUM)
                .termFactor(DEFAULT_FACTOR)
                .locationZoneId(null)
                .locationFactor(DEFAULT_FACTOR)
                .status(isActive(product)
                        ?PricingRuleStatus.ACTIVE
                        :PricingRuleStatus.DRAFT)
                .effectiveFrom(LocalDate.now())
                .createdBy(SYSTEM_USER_ID)
                .createdAt(LocalDateTime.now())
                .build();

        return repository.save(rule)
                .doOnNext(saved->log.info(
                        "Created default {} pricing rule {} for product {} ({})",
                        saved.getStatus(),
                        saved.getId(),
                        product.getProductId(),
                        product.getProductCode()
                ))
                .flatMap(saved->notifications.audit(
                        "PRICING_RULE_CREATED",
                        "PRICING_RULE",
                        saved.getId(),
                        null,
                        "Default pricing rule for productId="+
                                saved.getProductId()+
                                ",status="+saved.getStatus()
                ));
    }

    private Mono<Void> activateSystemDraft(List<PricingRule> rules){
        boolean hasActive=rules.stream()
                .anyMatch(rule->rule.getStatus()==PricingRuleStatus.ACTIVE);

        if(hasActive){
            return Mono.empty();
        }

        return rules.stream()
                .filter(rule->rule.getStatus()==PricingRuleStatus.DRAFT)
                .filter(rule->SYSTEM_USER_ID.equals(rule.getCreatedBy()))
                .filter(rule->rule.getUpdatedBy()==null)
                .filter(rule->rule.getEffectiveTo()==null||
                        !rule.getEffectiveTo().isBefore(LocalDate.now()))
                .findFirst()
                .map(rule->{
                    rule.setStatus(PricingRuleStatus.ACTIVE);
                    rule.setUpdatedAt(LocalDateTime.now());

                    return repository.save(rule)
                            .doOnNext(saved->log.info(
                                    "Activated default pricing rule {} for product {}",
                                    saved.getId(),
                                    saved.getProductId()
                            ))
                            .flatMap(saved->notifications.audit(
                                    "PRICING_RULE_STATUS_CHANGED",
                                    "PRICING_RULE",
                                    saved.getId(),
                                    null,
                                    "status="+saved.getStatus()
                            ));
                })
                .orElse(Mono.empty());
    }

    private boolean isActive(InternalProductResponse product){
        return "ACTIVE".equals(product.getStatus());
    }

    private BigDecimal rateFor(String category){
        if(category==null){
            return DEFAULT_RATE;
        }

        return CATEGORY_RATES.getOrDefault(
                category.trim().toUpperCase(),
                DEFAULT_RATE
        );
    }
}
