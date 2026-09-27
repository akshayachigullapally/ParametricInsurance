package org.example.financialservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.example.financialservice.dto.InternalProductResponse;
import org.example.financialservice.model.PricingRule;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.repository.PricingRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultPricingRuleProvisionerTest{
    private PricingRuleRepository repository;
    private ProductServiceClient productServiceClient;
    private DefaultPricingRuleProvisioner provisioner;

    @BeforeEach
    void setUp(){
        repository=mock(PricingRuleRepository.class);
        productServiceClient=mock(ProductServiceClient.class);
        NotificationService notifications=mock(NotificationService.class);

        when(notifications.audit(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString()
        )).thenReturn(Mono.empty());
        when(repository.save(any(PricingRule.class)))
                .thenAnswer(invocation->{
                    PricingRule rule=invocation.getArgument(0);
                    if(rule.getId()==null){
                        rule.setId(99L);
                    }
                    return Mono.just(rule);
                });

        provisioner=new DefaultPricingRuleProvisioner(
                repository,
                productServiceClient,
                notifications
        );
    }

    private InternalProductResponse product(
            Long id,
            String category,
            String status
    ){
        return InternalProductResponse.builder()
                .productId(id)
                .productCode("P-"+id)
                .category(category)
                .status(status)
                .build();
    }

    @Test
    void createsActiveDefaultRuleForActiveProductWithoutRules(){
        when(productServiceClient.findAllProducts())
                .thenReturn(Flux.just(product(1L,"FLOOD","ACTIVE")));
        when(repository.findAllByProductIdOrderByCreatedAtDesc(1L))
                .thenReturn(Flux.empty());

        StepVerifier.create(provisioner.provisionMissingRules())
                .verifyComplete();

        ArgumentCaptor<PricingRule> saved=
                ArgumentCaptor.forClass(PricingRule.class);
        verify(repository).save(saved.capture());

        PricingRule rule=saved.getValue();
        assertThat(rule.getProductId()).isEqualTo(1L);
        assertThat(rule.getStatus()).isEqualTo(PricingRuleStatus.ACTIVE);
        assertThat(rule.getBaseAnnualRate())
                .isEqualByComparingTo(new BigDecimal("0.045"));
        assertThat(rule.getLocationZoneId()).isNull();
        assertThat(rule.getCreatedBy())
                .isEqualTo(DefaultPricingRuleProvisioner.SYSTEM_USER_ID);
    }

    @Test
    void createsDraftDefaultRuleForDraftProduct(){
        when(productServiceClient.findAllProducts())
                .thenReturn(Flux.just(product(2L,"TRAVEL","DRAFT")));
        when(repository.findAllByProductIdOrderByCreatedAtDesc(2L))
                .thenReturn(Flux.empty());

        StepVerifier.create(provisioner.provisionMissingRules())
                .verifyComplete();

        ArgumentCaptor<PricingRule> saved=
                ArgumentCaptor.forClass(PricingRule.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus())
                .isEqualTo(PricingRuleStatus.DRAFT);
    }

    @Test
    void skipsArchivedProductsAndProductsWithAdminRules(){
        PricingRule adminRule=PricingRule.builder()
                .id(5L)
                .productId(3L)
                .status(PricingRuleStatus.DRAFT)
                .createdBy(1L)
                .effectiveFrom(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build();

        when(productServiceClient.findAllProducts())
                .thenReturn(Flux.just(
                        product(3L,"WEATHER","ACTIVE"),
                        product(4L,"WEATHER","ARCHIVED")
                ));
        when(repository.findAllByProductIdOrderByCreatedAtDesc(3L))
                .thenReturn(Flux.just(adminRule));

        StepVerifier.create(provisioner.provisionMissingRules())
                .verifyComplete();

        verify(repository,never()).save(any(PricingRule.class));
        verify(repository,never()).findAllByProductIdOrderByCreatedAtDesc(4L);
    }

    @Test
    void activatesSystemDraftOnceProductIsActive(){
        PricingRule systemDraft=PricingRule.builder()
                .id(7L)
                .productId(6L)
                .status(PricingRuleStatus.DRAFT)
                .createdBy(DefaultPricingRuleProvisioner.SYSTEM_USER_ID)
                .effectiveFrom(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build();

        when(productServiceClient.findAllProducts())
                .thenReturn(Flux.just(product(6L,"CYCLONE","ACTIVE")));
        when(repository.findAllByProductIdOrderByCreatedAtDesc(6L))
                .thenReturn(Flux.just(systemDraft));

        StepVerifier.create(provisioner.provisionMissingRules())
                .verifyComplete();

        assertThat(systemDraft.getStatus())
                .isEqualTo(PricingRuleStatus.ACTIVE);
        verify(repository).save(systemDraft);
    }
}
