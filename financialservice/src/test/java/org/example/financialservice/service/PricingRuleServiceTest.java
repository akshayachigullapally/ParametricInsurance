package org.example.financialservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.example.financialservice.TestAuth;
import org.example.financialservice.dto.InternalProductResponse;
import org.example.financialservice.dto.PricingRuleRequest;
import org.example.financialservice.model.PricingRule;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.repository.PricingRuleRepository;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PricingRuleServiceTest{
    private PricingRuleRepository repository;
    private ProductServiceClient productServiceClient;
    private TriggerServiceClient triggerServiceClient;
    private NotificationService notifications;
    private PricingRuleService service;

    @BeforeEach
    void setUp(){
        repository=mock(PricingRuleRepository.class);
        productServiceClient=mock(ProductServiceClient.class);
        triggerServiceClient=mock(TriggerServiceClient.class);
        notifications=mock(NotificationService.class);

        when(notifications.notifyPricingRuleCreated(
                any(),any(),any()
        )).thenReturn(Mono.empty());
        when(notifications.notifyPricingRuleUpdated(
                any(),any(),any()
        )).thenReturn(Mono.empty());

        when(triggerServiceClient.requireActiveZone(anyLong()))
                .thenReturn(Mono.empty());

        when(productServiceClient.requireActiveProduct(anyLong()))
                .thenReturn(Mono.empty());

        DefaultPricingRuleProvisioner defaultRules=
                mock(DefaultPricingRuleProvisioner.class);
        when(defaultRules.provisionMissingRules())
                .thenReturn(Mono.empty());

        service=new PricingRuleService(
                repository,
                productServiceClient,
                triggerServiceClient,
                notifications,
                defaultRules
        );
    }

    private PricingRuleRequest request(
            Long productId,
            Long zoneId
    ){
        return PricingRuleRequest.builder()
                .productId(productId)
                .baseAnnualRate(new BigDecimal("0.100000"))
                .minimumPremium(new BigDecimal("1000.00"))
                .termFactor(new BigDecimal("1.0000"))
                .locationZoneId(zoneId)
                .locationFactor(new BigDecimal("1.0000"))
                .effectiveFrom(LocalDate.of(2026,1,1))
                .effectiveTo(null)
                .build();
    }

    private PricingRule rule(
            Long id,
            Long productId,
            Long zoneId,
            PricingRuleStatus status
    ){
        return PricingRule.builder()
                .id(id)
                .productId(productId)
                .baseAnnualRate(new BigDecimal("0.100000"))
                .minimumPremium(new BigDecimal("1000.00"))
                .termFactor(new BigDecimal("1.0000"))
                .locationZoneId(zoneId)
                .locationFactor(new BigDecimal("1.0000"))
                .status(status)
                .effectiveFrom(LocalDate.of(2026,1,1))
                .createdAt(LocalDateTime.now())
                .createdBy(1L)
                .build();
    }

    @Test
    void createStartsAsDraft(){
        when(productServiceClient.findProduct(anyLong()))
                .thenReturn(Mono.just(InternalProductResponse.builder()
                        .productId(1L)
                        .status("ACTIVE")
                        .build()));
        when(repository.save(any(PricingRule.class)))
                .thenAnswer(invocation->
                        Mono.just(invocation.getArgument(0)));

        StepVerifier.create(TestAuth.admin(
                service.create(request(1L,null)),
                1L
        ))
                .assertNext(response->{
                    assertThat(response.getStatus())
                            .isEqualTo(PricingRuleStatus.DRAFT);
                    assertThat(response.getProductId()).isEqualTo(1L);
                })
                .verifyComplete();
    }

    @Test
    void multipleDraftsAreAllowedForSameProductAndZone(){
        when(productServiceClient.findProduct(anyLong()))
                .thenReturn(Mono.just(InternalProductResponse.builder()
                        .productId(1L)
                        .status("ACTIVE")
                        .build()));
        when(repository.save(any(PricingRule.class)))
                .thenAnswer(invocation->
                        Mono.just(invocation.getArgument(0)));

        PricingRuleRequest first=request(1L,2L);
        PricingRuleRequest second=request(1L,2L);

        StepVerifier.create(TestAuth.admin(
                service.create(first),
                1L
        )).expectNextCount(1).verifyComplete();

        StepVerifier.create(TestAuth.admin(
                service.create(second),
                1L
        )).expectNextCount(1).verifyComplete();
    }

    @Test
    void draftCanTransitionToActive(){
        PricingRule rule=rule(1L,1L,null,PricingRuleStatus.DRAFT);
        when(repository.findById(1L)).thenReturn(Mono.just(rule));
        when(repository.findAllByProductIdAndStatus(
                1L,
                PricingRuleStatus.ACTIVE
        )).thenReturn(Flux.empty());
        when(repository.save(any(PricingRule.class)))
                .thenAnswer(invocation->{
                    PricingRule saved=invocation.getArgument(0);
                    return Mono.just(saved);
                });

        StepVerifier.create(TestAuth.admin(
                service.updateStatus(
                        1L,
                        org.example.financialservice.dto
                                .UpdatePricingRuleStatusRequest.builder()
                                .status(PricingRuleStatus.ACTIVE)
                                .build()
                ),
                1L
        ))
                .assertNext(response->assertThat(
                        response.getStatus()
                ).isEqualTo(PricingRuleStatus.ACTIVE))
                .verifyComplete();
    }

    private org.example.financialservice.dto.UpdatePricingRuleStatusRequest activate(){
        return org.example.financialservice.dto.UpdatePricingRuleStatusRequest.builder()
                .status(PricingRuleStatus.ACTIVE)
                .build();
    }

    private void saveReturnsArgument(){
        when(repository.save(any(PricingRule.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
    }

    @Test
    void activatingReplacesTheCurrentDefaultRule(){
        PricingRule rule=rule(1L,1L,null,PricingRuleStatus.DRAFT);
        PricingRule current=rule(2L,1L,null,PricingRuleStatus.ACTIVE);

        when(repository.findById(1L)).thenReturn(Mono.just(rule));
        when(repository.findAllByProductIdAndStatus(1L,PricingRuleStatus.ACTIVE))
                .thenReturn(Flux.just(current));
        saveReturnsArgument();

        StepVerifier.create(TestAuth.admin(service.updateStatus(1L,activate()),1L))
                .assertNext(response->assertThat(response.getStatus())
                        .isEqualTo(PricingRuleStatus.ACTIVE))
                .verifyComplete();

        assertThat(current.getStatus()).isEqualTo(PricingRuleStatus.INACTIVE);
    }

    @Test
    void activatingAZoneRuleLeavesOtherZonesActive(){
        PricingRule rule=rule(1L,1L,2L,PricingRuleStatus.DRAFT);
        PricingRule sameZone=rule(2L,1L,2L,PricingRuleStatus.ACTIVE);
        PricingRule otherZone=rule(3L,1L,5L,PricingRuleStatus.ACTIVE);
        PricingRule defaultRule=rule(4L,1L,null,PricingRuleStatus.ACTIVE);

        when(repository.findById(1L)).thenReturn(Mono.just(rule));
        when(repository.findAllByProductIdAndStatus(1L,PricingRuleStatus.ACTIVE))
                .thenReturn(Flux.just(sameZone,otherZone,defaultRule));
        saveReturnsArgument();

        StepVerifier.create(TestAuth.admin(service.updateStatus(1L,activate()),1L))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(sameZone.getStatus()).isEqualTo(PricingRuleStatus.INACTIVE);
        assertThat(otherZone.getStatus()).isEqualTo(PricingRuleStatus.ACTIVE);
        assertThat(defaultRule.getStatus()).isEqualTo(PricingRuleStatus.ACTIVE);
    }

    @Test
    void futureDatedRuleIsActivatedAlongsideTheCurrentOne(){
        PricingRule rule=rule(1L,1L,null,PricingRuleStatus.DRAFT);
        rule.setEffectiveFrom(LocalDate.now().plusMonths(1));
        PricingRule current=rule(2L,1L,null,PricingRuleStatus.ACTIVE);

        when(repository.findById(1L)).thenReturn(Mono.just(rule));
        when(repository.findAllByProductIdAndStatus(1L,PricingRuleStatus.ACTIVE))
                .thenReturn(Flux.just(current));
        saveReturnsArgument();

        StepVerifier.create(TestAuth.admin(service.updateStatus(1L,activate()),1L))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(current.getStatus()).isEqualTo(PricingRuleStatus.ACTIVE);
    }

    @Test
    void activeRuleMustBeDeactivatedBeforeArchive(){
        PricingRule rule=rule(1L,1L,null,PricingRuleStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Mono.just(rule));

        StepVerifier.create(TestAuth.admin(
                service.updateStatus(
                        1L,
                        org.example.financialservice.dto
                                .UpdatePricingRuleStatusRequest.builder()
                                .status(PricingRuleStatus.ARCHIVED)
                                .build()
                ),
                1L
        ))
                .expectError(ConflictException.class)
                .verify();

        StepVerifier.create(TestAuth.admin(
                service.archive(1L),
                1L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void archivedRuleCannotTransition(){
        PricingRule rule=rule(1L,1L,null,PricingRuleStatus.ARCHIVED);
        when(repository.findById(1L)).thenReturn(Mono.just(rule));

        StepVerifier.create(TestAuth.admin(
                service.updateStatus(
                        1L,
                        org.example.financialservice.dto
                                .UpdatePricingRuleStatusRequest.builder()
                                .status(PricingRuleStatus.ACTIVE)
                                .build()
                ),
                1L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void archivedRuleCanBeRestoredAsInactive(){
        PricingRule rule=rule(1L,1L,null,PricingRuleStatus.ARCHIVED);
        when(repository.findById(1L)).thenReturn(Mono.just(rule));
        saveReturnsArgument();

        StepVerifier.create(TestAuth.admin(
                service.updateStatus(
                        1L,
                        org.example.financialservice.dto
                                .UpdatePricingRuleStatusRequest.builder()
                                .status(PricingRuleStatus.INACTIVE)
                                .build()
                ),
                1L
        ))
                .assertNext(response->assertThat(response.getStatus()).isEqualTo(PricingRuleStatus.INACTIVE))
                .verifyComplete();
    }

    @Test
    void archivedRuleCannotBeEdited(){
        PricingRule rule=rule(1L,1L,null,PricingRuleStatus.ARCHIVED);
        when(productServiceClient.findProduct(anyLong()))
                .thenReturn(Mono.just(InternalProductResponse.builder()
                        .productId(1L)
                        .status("ACTIVE")
                        .build()));
        when(repository.findById(1L)).thenReturn(Mono.just(rule));

        StepVerifier.create(TestAuth.admin(
                service.update(1L,request(1L,null)),
                1L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void activeRuleCannotBeEdited(){
        PricingRule rule=rule(1L,1L,null,PricingRuleStatus.ACTIVE);
        when(productServiceClient.findProduct(anyLong()))
                .thenReturn(Mono.just(InternalProductResponse.builder()
                        .productId(1L)
                        .status("ACTIVE")
                        .build()));
        when(repository.findById(1L)).thenReturn(Mono.just(rule));

        StepVerifier.create(TestAuth.admin(
                service.update(1L,request(1L,null)),
                1L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void activationRequiresActiveProduct(){
        PricingRule rule=rule(1L,1L,null,PricingRuleStatus.DRAFT);
        when(repository.findById(1L)).thenReturn(Mono.just(rule));
        when(repository.findAllByProductIdAndStatus(
                1L,
                PricingRuleStatus.ACTIVE
        )).thenReturn(Flux.empty());
        when(productServiceClient.requireActiveProduct(1L))
                .thenReturn(Mono.error(
                        new ConflictException("Product is not active")
                ));

        StepVerifier.create(TestAuth.admin(
                service.updateStatus(
                        1L,
                        org.example.financialservice.dto
                                .UpdatePricingRuleStatusRequest.builder()
                                .status(PricingRuleStatus.ACTIVE)
                                .build()
                ),
                1L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void expiredRuleCannotBeActivated(){
        PricingRule rule=rule(1L,1L,null,PricingRuleStatus.DRAFT);
        rule.setEffectiveTo(LocalDate.of(2020,1,1));

        when(repository.findById(1L)).thenReturn(Mono.just(rule));

        StepVerifier.create(TestAuth.admin(
                service.updateStatus(
                        1L,
                        org.example.financialservice.dto
                                .UpdatePricingRuleStatusRequest.builder()
                                .status(PricingRuleStatus.ACTIVE)
                                .build()
                ),
                1L
        ))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void ratesAboveMaximumAreRejected(){
        PricingRuleRequest bad=request(1L,null);
        bad.setBaseAnnualRate(new BigDecimal("1.500000"));

        assertThrows(ValidationException.class,
                ()->service.create(bad));
    }

    @Test
    void termFactorAboveTenIsRejected(){
        PricingRuleRequest bad=request(1L,null);
        bad.setTermFactor(new BigDecimal("11.0000"));

        assertThrows(ValidationException.class,
                ()->service.create(bad));
    }

    @Test
    void locationFactorZeroIsRejected(){
        PricingRuleRequest bad=request(1L,null);
        bad.setLocationFactor(BigDecimal.ZERO);

        assertThrows(ValidationException.class,
                ()->service.create(bad));
    }

    @Test
    void negativeMinimumPremiumIsRejected(){
        PricingRuleRequest bad=request(1L,null);
        bad.setMinimumPremium(new BigDecimal("-10.00"));

        assertThrows(ValidationException.class,
                ()->service.create(bad));
    }

    @Test
    void effectiveToBeforeEffectiveFromIsRejected(){
        PricingRuleRequest bad=request(1L,null);
        bad.setEffectiveFrom(LocalDate.of(2026,6,1));
        bad.setEffectiveTo(LocalDate.of(2026,1,1));

        assertThrows(ValidationException.class,
                ()->service.create(bad));
    }
}