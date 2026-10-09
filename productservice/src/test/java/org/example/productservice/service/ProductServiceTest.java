package org.example.productservice.service;

import org.example.productservice.client.TriggerServiceClient;
import org.example.productservice.dto.*;
import org.example.productservice.exception.*;
import org.example.productservice.model.*;
import org.example.productservice.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest{
    @Mock private ProductRepository productRepository;
    @Mock private CoverageRepository coverageRepository;
    @Mock private NotificationService notificationService;
    @Mock private TriggerServiceClient triggerServiceClient;
    @Mock private ProductThresholdHistoryRepository thresholdHistoryRepository;
    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyTriggerSnapshotRepository snapshotRepository;
    @InjectMocks private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp(){
        lenient().when(notificationService.notifyProductCreated(any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.notifyProductUpdated(any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(triggerServiceClient.syncProductDefinition(any()))
                .thenReturn(Mono.empty());
        lenient().when(thresholdHistoryRepository
                        .save(any(ProductThresholdHistory.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        product=Product.builder()
                .id(10L)
                .productCode("WEATHER-01")
                .name("Weather Shield")
                .description("Weather parametric product")
                .category("WEATHER")
                .status(ProductStatus.DRAFT.name())
                .minimumSumInsured(new BigDecimal("10000"))
                .maximumSumInsured(new BigDecimal("500000"))
                .minimumTermMonths(3)
                .maximumTermMonths(24)
                .eligibilityDescription("Standard eligibility")
                .perilCode("EXCESS_RAINFALL")
                .measurementType("RAINFALL")
                .measurementUnit("mm")
                .thresholdValue(new BigDecimal("150.00"))
                .comparisonOperator("GREATER_THAN_OR_EQUAL")
                .aggregationType("SUM")
                .observationWindowHours(24)
                .minimumObservationCount(1)
                .triggerDescription(
                        "Coverage is triggered when total rainfall reaches or exceeds 150 mm within 24 hours."
                )
                .triggerSyncStatus("PENDING")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ProductRequest createValidRequest(){
        return ProductRequest.builder()
                .productCode("WEATHER-02")
                .name("Storm Cover")
                .description("Storm protection")
                .category("WEATHER")
                .minimumSumInsured(new BigDecimal("5000"))
                .maximumSumInsured(new BigDecimal("200000"))
                .minimumTermMonths(3)
                .maximumTermMonths(12)
                .eligibilityDescription("Must be in flood zone")
                .perilCode("EXCESS_RAINFALL")
                .measurementType("RAINFALL")
                .measurementUnit("mm")
                .thresholdValue(new BigDecimal("150.00"))
                .comparisonOperator("GREATER_THAN_OR_EQUAL")
                .aggregationType("SUM")
                .observationWindowHours(24)
                .minimumObservationCount(1)
                .build();
    }

    @Test
    void createProduct_whenDuplicateCode_shouldReturnConflict(){
        ProductRequest request=createValidRequest();

        when(productRepository.existsByProductCodeIgnoreCase("WEATHER-02"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(productService.createProduct(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Product code already exists");
                })
                .verify();

        verify(productRepository,never()).save(any(Product.class));
    }

    @Test
    void createProduct_whenCategoryIsInvalid_shouldThrowValidationException(){
        ProductRequest request=createValidRequest();
        request.setCategory("INVALID_CATEGORY");

        when(productRepository.existsByProductCodeIgnoreCase("WEATHER-02"))
                .thenReturn(Mono.just(false));

        StepVerifier.create(productService.createProduct(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).contains("Category must be");
                })
                .verify();
    }

    @Test
    void createProduct_withCoverages_shouldSaveCoverages(){
        ProductRequest request=createValidRequest();
        CoverageRequest coverage=CoverageRequest.builder()
                .coverageCode("COV-001")
                .name("Basic Coverage")
                .description("Standard coverage")
                .coverageLimit(new BigDecimal("100000"))
                .mandatory(true)
                .active(true)
                .build();
        request.setCoverages(List.of(coverage));

        when(productRepository.existsByProductCodeIgnoreCase("WEATHER-02"))
                .thenReturn(Mono.just(false));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation->{
                    Product saved=invocation.getArgument(0);
                    saved.setId(20L);
                    return Mono.just(saved);
                });

        when(coverageRepository.save(any(Coverage.class)))
                .thenAnswer(invocation->{
                    Coverage saved=invocation.getArgument(0);
                    saved.setId(1L);
                    return Mono.just(saved);
                });

        when(coverageRepository.findAllByProductId(20L))
                .thenReturn(Flux.empty());

        StepVerifier.create(productService.createProduct(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(20L);
                })
                .verifyComplete();

        verify(coverageRepository).save(argThat(c->
                c.getCoverageCode().equals("COV-001")&&
                        c.getProductId().equals(20L)&&
                        c.getMandatory().equals(true)
        ));
    }

    @Test
    void createProduct_withDuplicateCoverageCodes_shouldThrowValidationException(){
        ProductRequest request=createValidRequest();
        CoverageRequest cov1=CoverageRequest.builder()
                .coverageCode("COV-001")
                .name("Coverage 1")
                .build();
        CoverageRequest cov2=CoverageRequest.builder()
                .coverageCode("cov-001")
                .name("Coverage 2")
                .build();
        request.setCoverages(List.of(cov1,cov2));

        when(productRepository.existsByProductCodeIgnoreCase("WEATHER-02"))
                .thenReturn(Mono.just(false));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation->{
                    Product saved=invocation.getArgument(0);
                    saved.setId(20L);
                    return Mono.just(saved);
                });

        StepVerifier.create(productService.createProduct(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .contains("Duplicate coverage code");
                })
                .verify();
    }

    @Test
    void getProductsByCategory_shouldReturnFilteredProducts(){
        product.setStatus(ProductStatus.ACTIVE.name());

        when(productRepository.findAllByCategoryAndStatus("WEATHER","ACTIVE"))
                .thenReturn(Flux.just(product));

        when(coverageRepository.findAllByProductIdAndActiveTrue(10L))
                .thenReturn(Flux.empty());

        StepVerifier.create(productService.getProductsByCategory("WEATHER"))
                .assertNext(response->{
                    assertThat(response.getCategory()).isEqualTo("WEATHER");
                })
                .verifyComplete();
    }

    @Test
    void updateStatus_whenValid_shouldUpdateStatus(){
        UpdateProductStatusRequest request=UpdateProductStatusRequest.builder()
                .status("ACTIVE")
                .build();

        when(productRepository.findById(10L))
                .thenReturn(Mono.just(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        when(coverageRepository.findAllByProductId(10L))
                .thenReturn(Flux.empty());

        StepVerifier.create(productService.updateStatus(10L,request))
                .assertNext(response->{
                    assertThat(response.getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();

        verify(productRepository, atLeastOnce()).save(argThat(p->
                p.getStatus().equals("ACTIVE")
        ));
    }

    @Test
    void updateStatus_whenInvalidStatus_shouldThrowValidationException(){
        UpdateProductStatusRequest request=UpdateProductStatusRequest.builder()
                .status("INVALID")
                .build();

        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->productService.updateStatus(10L,request)
        );

        assertThat(exception.getMessage()).contains("Status must be");
    }

    @Test
    void deleteProduct_whenFound_shouldArchiveProduct(){
        when(productRepository.findById(10L))
                .thenReturn(Mono.just(product));

        when(policyRepository.findAllByProductIdAndStatus(eq(10L),anyString()))
                .thenReturn(Flux.empty());

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(productService.deleteProduct(10L))
                .assertNext(response->{
                    assertThat(response.getMessage())
                            .isEqualTo("Product archived successfully");
                })
                .verifyComplete();

        verify(productRepository, atLeastOnce()).save(argThat(p->
                p.getStatus().equals("ARCHIVED")
        ));
    }

    @Test
    void updateProduct_whenSameCodeAsCurrent_shouldNotConflict(){
        ProductRequest request=createValidRequest();
        request.setProductCode("WEATHER-01");

        when(productRepository.findById(10L))
                .thenReturn(Mono.just(product));

        when(productRepository.findByProductCodeIgnoreCase("WEATHER-01"))
                .thenReturn(Mono.just(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        when(coverageRepository.deleteAllByProductId(10L))
                .thenReturn(Mono.empty());

        when(coverageRepository.findAllByProductId(10L))
                .thenReturn(Flux.empty());

        StepVerifier.create(productService.updateProduct(10L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(10L);
                    assertThat(response.getProductCode())
                            .isEqualTo("WEATHER-01");
                })
                .verifyComplete();

        verify(productRepository, atLeastOnce())
                .save(any(Product.class));
    }

    @Test
    void updateProduct_withCoverages_shouldReplaceCoverages(){
        ProductRequest request=createValidRequest();
        CoverageRequest coverage=CoverageRequest.builder()
                .coverageCode("COV-NEW")
                .name("New Coverage")
                .coverageLimit(new BigDecimal("50000"))
                .mandatory(true)
                .active(true)
                .build();
        request.setCoverages(List.of(coverage));

        when(productRepository.findById(10L))
                .thenReturn(Mono.just(product));

        when(productRepository.findByProductCodeIgnoreCase("WEATHER-02"))
                .thenReturn(Mono.empty());

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        when(coverageRepository.deleteAllByProductId(10L))
                .thenReturn(Mono.empty());

        when(coverageRepository.save(any(Coverage.class)))
                .thenAnswer(invocation->{
                    Coverage saved=invocation.getArgument(0);
                    saved.setId(5L);
                    return Mono.just(saved);
                });

        when(coverageRepository.findAllByProductId(10L))
                .thenReturn(Flux.empty());

        StepVerifier.create(productService.updateProduct(10L,request))
                .assertNext(response->assertThat(response.getId()).isEqualTo(10L))
                .verifyComplete();

        verify(coverageRepository).deleteAllByProductId(10L);
        verify(coverageRepository).save(argThat(c->
                c.getCoverageCode().equals("COV-NEW")&&
                        c.getProductId().equals(10L)
        ));
    }

    @Test
    void updateStatus_whenStatusIsArchived_shouldUpdateStatus(){
        UpdateProductStatusRequest request=UpdateProductStatusRequest.builder()
                .status("ARCHIVED")
                .build();

        when(productRepository.findById(10L))
                .thenReturn(Mono.just(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        when(coverageRepository.findAllByProductId(10L))
                .thenReturn(Flux.empty());
        when(policyRepository.findAllByProductIdAndStatus(10L,"PENDING"))
                .thenReturn(Flux.empty());
        when(policyRepository.findAllByProductIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Flux.empty());

        StepVerifier.create(productService.updateStatus(10L,request))
                .assertNext(response->{
                    assertThat(response.getStatus()).isEqualTo("ARCHIVED");
                })
                .verifyComplete();
    }

    @Test
    void getActiveProducts_withCoverages_shouldIncludeCoverages(){
        product.setStatus(ProductStatus.ACTIVE.name());

        Coverage coverage=Coverage.builder()
                .id(1L)
                .productId(10L)
                .coverageCode("COV-001")
                .name("Basic Coverage")
                .coverageLimit(new BigDecimal("100000"))
                .mandatory(true)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(productRepository.findAllByStatus("ACTIVE"))
                .thenReturn(Flux.just(product));

        when(coverageRepository.findAllByProductIdAndActiveTrue(10L))
                .thenReturn(Flux.just(coverage));

        StepVerifier.create(productService.getActiveProducts())
                .assertNext(response->{
                    assertThat(response.getCoverages()).hasSize(1);
                    assertThat(response.getCoverages().get(0).getCoverageCode())
                            .isEqualTo("COV-001");
                })
                .verifyComplete();
    }

    @Test
    void getAllProducts_withCoverages_shouldIncludeCoverages(){
        Coverage coverage=Coverage.builder()
                .id(1L)
                .productId(10L)
                .coverageCode("COV-001")
                .name("Basic Coverage")
                .coverageLimit(new BigDecimal("100000"))
                .mandatory(true)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(productRepository.findAll())
                .thenReturn(Flux.just(product));

        when(coverageRepository.findAllByProductId(10L))
                .thenReturn(Flux.just(coverage));

        StepVerifier.create(productService.getAllProducts())
                .assertNext(response->{
                    assertThat(response.getCoverages()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void getProduct_withCoverages_shouldIncludeCoverages(){
        Coverage coverage=Coverage.builder()
                .id(1L)
                .productId(10L)
                .coverageCode("COV-001")
                .name("Basic Coverage")
                .coverageLimit(new BigDecimal("100000"))
                .mandatory(true)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById(10L))
                .thenReturn(Mono.just(product));

        when(coverageRepository.findAllByProductId(10L))
                .thenReturn(Flux.just(coverage));

        StepVerifier.create(productService.getProduct(10L))
                .assertNext(response->{
                    assertThat(response.getCoverages()).hasSize(1);
                    assertThat(response.getCoverages().get(0).getMandatory())
                            .isTrue();
                })
                .verifyComplete();
    }

    @Test
    void createProduct_whenCategoryIsBlank_shouldThrowValidationException(){
        ProductRequest request=createValidRequest();
        request.setCategory("   ");

        when(productRepository.existsByProductCodeIgnoreCase("WEATHER-02"))
                .thenReturn(Mono.just(false));

        StepVerifier.create(productService.createProduct(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Product category is required");
                })
                .verify();
    }

    @Test
    void createProduct_whenInvalidMeasurementUnit_shouldThrowValidationException(){
        ProductRequest request=createValidRequest();
        request.setMeasurementUnit("gallon");

        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->productService.createProduct(1L,request)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Invalid measurement unit: gallon");
    }

    @Test
    void createProduct_whenMeasurementUnitMismatch_shouldThrowValidationException(){
        ProductRequest request=createValidRequest();
        request.setMeasurementUnit("m");

        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->productService.createProduct(1L,request)
        );

        assertThat(exception.getMessage())
                .isEqualTo("RAINFALL measurement must use mm as its unit");
    }

    @Test
    void updateThreshold_whenRequestIsValid_shouldUpdateAndRecordHistory(){
        ProductThresholdUpdateRequest request=thresholdRequest("185.00");

        when(productRepository.findById(10L))
                .thenReturn(Mono.just(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(productService.updateThreshold(2L,10L,request))
                .assertNext(response->{
                    assertThat(response.getProductId()).isEqualTo(10L);
                    assertThat(response.getNewThresholdValue())
                            .isEqualByComparingTo(new BigDecimal("185.00"));
                    assertThat(response.getPreviousThresholdValue())
                            .isEqualByComparingTo(new BigDecimal("150.00"));
                    assertThat(response.getMeasurementUnit()).isEqualTo("mm");
                    assertThat(response.getExistingPoliciesUpdated()).isFalse();
                })
                .verifyComplete();

        verify(thresholdHistoryRepository).save(argThat(h->
                h.getProductId().equals(10L)&&
                        h.getPreviousThresholdValue().compareTo(
                                new BigDecimal("150.00")
                        )==0&&
                        h.getNewThresholdValue().compareTo(
                                new BigDecimal("185.00")
                        )==0&&
                        h.getChangedBy().equals(2L)&&
                        h.getChangeReason().equals("Re-rated threshold")
        ));
    }

    @Test
    void getThresholdHistory_whenFound_shouldReturnHistory(){
        ProductThresholdHistory history=ProductThresholdHistory.builder()
                .id(1L)
                .productId(10L)
                .productCode("WEATHER-01")
                .previousThresholdValue(new BigDecimal("150.00"))
                .newThresholdValue(new BigDecimal("185.00"))
                .changedBy(2L)
                .changeReason("Re-rated")
                .changedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById(10L))
                .thenReturn(Mono.just(product));

        when(thresholdHistoryRepository
                .findAllByProductIdOrderByChangedAtDesc(10L))
                .thenReturn(Flux.just(history));

        StepVerifier.create(productService.getThresholdHistory(10L))
                .assertNext(response->{
                    assertThat(response.getProductId()).isEqualTo(10L);
                    assertThat(response.getNewThresholdValue())
                            .isEqualByComparingTo(new BigDecimal("185.00"));
                    assertThat(response.getChangeReason()).isEqualTo("Re-rated");
                })
                .verifyComplete();
    }

    @Test
    void syncTrigger_whenSyncSucceeds_shouldReturnSyncedStatus(){
        when(productRepository.findById(10L))
                .thenReturn(Mono.just(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(productService.syncTrigger(1L,10L))
                .assertNext(response->{
                    assertThat(response.getProductId()).isEqualTo(10L);
                    assertThat(response.getSyncStatus()).isEqualTo("SYNCED");
                })
                .verifyComplete();
    }

    @Test
    void syncTrigger_whenSyncFails_shouldReturnFailedStatus(){
        when(triggerServiceClient.syncProductDefinition(any()))
                .thenReturn(Mono.error(new RuntimeException(
                        "Trigger service unavailable"
                )));

        when(productRepository.findById(10L))
                .thenReturn(Mono.just(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(productService.syncTrigger(1L,10L))
                .assertNext(response->{
                    assertThat(response.getProductId()).isEqualTo(10L);
                    assertThat(response.getSyncStatus()).isEqualTo("FAILED");
                    assertThat(response.getMessage())
                            .contains("Trigger service unavailable");
                })
                .verifyComplete();
    }

    @Test
    void getActiveProduct_whenTriggerComplete_shouldIncludeTriggerFields(){
        product.setStatus(ProductStatus.ACTIVE.name());

        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        when(coverageRepository.findAllByProductIdAndActiveTrue(10L))
                .thenReturn(Flux.empty());

        StepVerifier.create(productService.getActiveProduct(10L))
                .assertNext(response->{
                    assertThat(response.getThresholdValue())
                            .isEqualByComparingTo(new BigDecimal("150.00"));
                    assertThat(response.getComparisonOperator())
                            .isEqualTo("GREATER_THAN_OR_EQUAL");
                    assertThat(response.getAggregationType()).isEqualTo("SUM");
                    assertThat(response.getObservationWindowHours()).isEqualTo(24);
                    assertThat(response.getMinimumObservationCount()).isEqualTo(1);
                    assertThat(response.getHumanReadableTrigger())
                            .isEqualTo("Total rainfall >= 150 mm within 24 hours");
                    assertThat(response.getTriggerDescription())
                            .isEqualTo("Coverage is triggered when total rainfall reaches or exceeds 150 mm within 24 hours.");
                })
                .verifyComplete();
    }

    private ProductThresholdUpdateRequest thresholdRequest(String thresholdValue){
        return ProductThresholdUpdateRequest.builder()
                .perilCode("EXCESS_RAINFALL")
                .measurementType("RAINFALL")
                .measurementUnit("mm")
                .thresholdValue(new BigDecimal(thresholdValue))
                .comparisonOperator("GREATER_THAN_OR_EQUAL")
                .aggregationType("SUM")
                .observationWindowHours(24)
                .minimumObservationCount(1)
                .changeReason("Re-rated threshold")
                .build();
    }
}
