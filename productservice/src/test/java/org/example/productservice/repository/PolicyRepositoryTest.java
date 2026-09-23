package org.example.productservice.repository;

import org.example.productservice.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@ActiveProfiles("test")
class PolicyRepositoryTest{
    @Autowired private PolicyRepository policyRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PolicyTriggerSnapshotRepository policyTriggerSnapshotRepository;
    @Autowired private PolicyStatusHistoryRepository policyStatusHistoryRepository;
    @Autowired private CoverageRepository coverageRepository;
    @Autowired private ProductThresholdHistoryRepository productThresholdHistoryRepository;

    private Product savedProduct;

    @BeforeEach
    void setUp(){
        // Other @DataR2dbcTest classes in this module share the same persistent H2
        // instance within one Maven fork, so every FK-dependent child table must be
        // cleared before policies/products themselves, regardless of run order.
        policyTriggerSnapshotRepository.deleteAll().block();
        policyStatusHistoryRepository.deleteAll().block();
        productThresholdHistoryRepository.deleteAll().block();
        coverageRepository.deleteAll().block();
        policyRepository.deleteAll().block();
        productRepository.deleteAll().block();

        Product product=Product.builder()
                .productCode("WEATHER-01")
                .name("Weather Shield")
                .category("WEATHER")
                .status(ProductStatus.ACTIVE.name())
                .minimumSumInsured(new BigDecimal("10000"))
                .maximumSumInsured(new BigDecimal("500000"))
                .minimumTermMonths(3)
                .maximumTermMonths(24)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        savedProduct=productRepository.save(product).block();
    }

    @AfterEach
    void tearDown(){
        policyTriggerSnapshotRepository.deleteAll().block();
        policyStatusHistoryRepository.deleteAll().block();
        productThresholdHistoryRepository.deleteAll().block();
        coverageRepository.deleteAll().block();
        policyRepository.deleteAll().block();
        productRepository.deleteAll().block();
    }

    private Policy createPolicy(String policyNumber,Long customerId,String status){
        return Policy.builder()
                .policyNumber(policyNumber)
                .customerId(customerId)
                .productId(savedProduct.getId())
                .productCode(savedProduct.getProductCode())
                .productName(savedProduct.getName())
                .locationZoneId(7L)
                .sumInsured(new BigDecimal("120000"))
                .premiumAmount(new BigDecimal("1200.00"))
                .termMonths(6)
                .status(status)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void save_shouldPersistPolicy(){
        Policy policy=createPolicy("POL-2026-001",1L,"PENDING");

        StepVerifier.create(policyRepository.save(policy))
                .assertNext(saved->{
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getPolicyNumber()).isEqualTo("POL-2026-001");
                    assertThat(saved.getCustomerId()).isEqualTo(1L);
                    assertThat(saved.getStatus()).isEqualTo("PENDING");
                })
                .verifyComplete();
    }

    @Test
    void findByPolicyNumber_shouldFindPolicy(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();

        StepVerifier.create(policyRepository.findByPolicyNumber("POL-2026-001"))
                .assertNext(found->{
                    assertThat(found.getPolicyNumber()).isEqualTo("POL-2026-001");
                })
                .verifyComplete();
    }

    @Test
    void findByPolicyNumber_whenNotExists_shouldReturnEmpty(){
        StepVerifier.create(policyRepository.findByPolicyNumber("NONEXISTENT"))
                .verifyComplete();
    }

    @Test
    void existsByPolicyNumber_whenExists_shouldReturnTrue(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();

        StepVerifier.create(policyRepository.existsByPolicyNumber("POL-2026-001"))
                .assertNext(exists->assertThat(exists).isTrue())
                .verifyComplete();
    }

    @Test
    void existsByPolicyNumber_whenNotExists_shouldReturnFalse(){
        StepVerifier.create(policyRepository.existsByPolicyNumber("NONEXISTENT"))
                .assertNext(exists->assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void findAllByCustomerId_shouldReturnCustomerPolicies(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();
        policyRepository.save(createPolicy("POL-2026-002",1L,"ACTIVE")).block();
        policyRepository.save(createPolicy("POL-2026-003",2L,"PENDING")).block();

        StepVerifier.create(policyRepository.findAllByCustomerId(1L))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findAllByCustomerIdAndStatus_shouldReturnFilteredPolicies(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();
        policyRepository.save(createPolicy("POL-2026-002",1L,"ACTIVE")).block();
        policyRepository.save(createPolicy("POL-2026-003",1L,"PENDING")).block();

        StepVerifier.create(policyRepository.findAllByCustomerIdAndStatus(1L,"PENDING"))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findAllByStatus_shouldReturnMatchingPolicies(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();
        policyRepository.save(createPolicy("POL-2026-002",2L,"ACTIVE")).block();
        policyRepository.save(createPolicy("POL-2026-003",3L,"PENDING")).block();

        StepVerifier.create(policyRepository.findAllByStatus("PENDING"))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findByIdAndCustomerId_shouldReturnMatchingPolicy(){
        Policy saved=policyRepository
                .save(createPolicy("POL-2026-001",1L,"PENDING"))
                .block();

        StepVerifier.create(policyRepository.findByIdAndCustomerId(saved.getId(),1L))
                .assertNext(found->{
                    assertThat(found.getId()).isEqualTo(saved.getId());
                    assertThat(found.getCustomerId()).isEqualTo(1L);
                })
                .verifyComplete();
    }

    @Test
    void findByIdAndCustomerId_whenCustomerMismatch_shouldReturnEmpty(){
        Policy saved=policyRepository
                .save(createPolicy("POL-2026-001",1L,"PENDING"))
                .block();

        StepVerifier.create(policyRepository.findByIdAndCustomerId(saved.getId(),2L))
                .verifyComplete();
    }

    @Test
    void existsByCustomerIdAndProductIdAndStatus_shouldReturnTrue(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();

        StepVerifier.create(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,savedProduct.getId(),"PENDING"
        ))
                .assertNext(exists->assertThat(exists).isTrue())
                .verifyComplete();
    }

    @Test
    void existsByCustomerIdAndProductIdAndStatus_whenNoMatch_shouldReturnFalse(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"ACTIVE")).block();

        StepVerifier.create(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,savedProduct.getId(),"PENDING"
        ))
                .assertNext(exists->assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void deleteAll_shouldRemoveAllPolicies(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();
        policyRepository.save(createPolicy("POL-2026-002",2L,"ACTIVE")).block();

        policyRepository.deleteAll().block();

        StepVerifier.create(policyRepository.findAll())
                .verifyComplete();
    }

    @Test
    void findAllByCustomerId_whenNoMatch_shouldReturnEmpty(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();

        StepVerifier.create(policyRepository.findAllByCustomerId(99L))
                .verifyComplete();
    }

    @Test
    void findAllByCustomerIdAndStatus_whenNoMatch_shouldReturnEmpty(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"ACTIVE")).block();

        StepVerifier.create(policyRepository.findAllByCustomerIdAndStatus(1L,"PENDING"))
                .verifyComplete();
    }

    @Test
    void findAllByStatus_whenNoMatch_shouldReturnEmpty(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"ACTIVE")).block();

        StepVerifier.create(policyRepository.findAllByStatus("CANCELLED"))
                .verifyComplete();
    }

    @Test
    void save_shouldPersistAllPolicyFields(){
        Policy policy=Policy.builder()
                .policyNumber("POL-2026-FULL")
                .customerId(5L)
                .productId(savedProduct.getId())
                .productCode(savedProduct.getProductCode())
                .productName(savedProduct.getName())
                .locationZoneId(10L)
                .sumInsured(new BigDecimal("250000"))
                .premiumAmount(new BigDecimal("2500.00"))
                .termMonths(12)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(12))
                .status("ACTIVE")
                .rejectionReason(null)
                .reviewedBy(55L)
                .reviewedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        StepVerifier.create(policyRepository.save(policy))
                .assertNext(saved->{
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getPolicyNumber()).isEqualTo("POL-2026-FULL");
                    assertThat(saved.getCustomerId()).isEqualTo(5L);
                    assertThat(saved.getLocationZoneId()).isEqualTo(10L);
                    assertThat(saved.getSumInsured())
                            .isEqualByComparingTo(new BigDecimal("250000"));
                    assertThat(saved.getPremiumAmount())
                            .isEqualByComparingTo(new BigDecimal("2500.00"));
                    assertThat(saved.getTermMonths()).isEqualTo(12);
                    assertThat(saved.getStartDate()).isNotNull();
                    assertThat(saved.getEndDate()).isNotNull();
                    assertThat(saved.getStatus()).isEqualTo("ACTIVE");
                    assertThat(saved.getReviewedBy()).isEqualTo(55L);
                    assertThat(saved.getReviewedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void existsByCustomerIdAndProductIdAndStatus_withDifferentProduct_shouldReturnFalse(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();

        StepVerifier.create(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,999L,"PENDING"
        ))
                .assertNext(exists->assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void findAllByCustomerIdAndStatus_shouldReturnEmptyForDifferentStatus(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();
        policyRepository.save(createPolicy("POL-2026-002",1L,"ACTIVE")).block();

        StepVerifier.create(policyRepository.findAllByCustomerIdAndStatus(1L,"REJECTED"))
                .verifyComplete();
    }

    @Test
    void save_withNullOptionalFields_shouldPersist(){
        Policy policy=Policy.builder()
                .policyNumber("POL-2026-NULL")
                .customerId(1L)
                .productId(savedProduct.getId())
                .productCode(savedProduct.getProductCode())
                .productName(savedProduct.getName())
                .locationZoneId(7L)
                .sumInsured(new BigDecimal("100000"))
                .premiumAmount(new BigDecimal("1000.00"))
                .termMonths(6)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        StepVerifier.create(policyRepository.save(policy))
                .assertNext(saved->{
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getStartDate()).isNull();
                    assertThat(saved.getEndDate()).isNull();
                    assertThat(saved.getRejectionReason()).isNull();
                    assertThat(saved.getReviewedBy()).isNull();
                    assertThat(saved.getReviewedAt()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void deleteAll_shouldNotAffectProducts(){
        policyRepository.save(createPolicy("POL-2026-001",1L,"PENDING")).block();

        policyRepository.deleteAll().block();

        StepVerifier.create(productRepository.findAll())
                .expectNextCount(1)
                .verifyComplete();
    }
}
