package org.example.productservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.productservice.model.*;
import org.example.productservice.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductDataLoader implements ApplicationRunner{
    private final ProductRepository productRepository;
    private final CoverageRepository coverageRepository;

    @Override
    public void run(ApplicationArguments args){
        Mono.when(
                loadProduct(WEATHER_SEED),
                loadProduct(FLOOD_SEED)
        ).doOnSuccess(ignored->
                log.info("Product data initialization completed"))
                .doOnError(error->
                        log.error("Product data initialization failed",error))
                .subscribe();
    }

    private Mono<Void> loadProduct(ProductSeed seed){
        return productRepository.findByProductCodeIgnoreCase(seed.productCode)
                .flatMap(existing->patchMissingTriggerFields(existing,seed))
                .switchIfEmpty(Mono.defer(()->createProduct(seed)))
                .flatMap(saved->ensureDefaultCoverage(saved))
                .then();
    }

    private Mono<Product> patchMissingTriggerFields(Product product,ProductSeed seed){
        boolean changed=false;

        if(!StringUtils.hasText(product.getPerilCode())){
            product.setPerilCode(seed.perilCode);
            changed=true;
        }

        if(!StringUtils.hasText(product.getMeasurementType())){
            product.setMeasurementType(seed.measurementType);
            changed=true;
        }

        if(!StringUtils.hasText(product.getMeasurementUnit())){
            product.setMeasurementUnit(seed.measurementUnit);
            changed=true;
        }

        if(product.getThresholdValue()==null){
            product.setThresholdValue(seed.thresholdValue);
            changed=true;
        }

        if(!StringUtils.hasText(product.getComparisonOperator())){
            product.setComparisonOperator(seed.comparisonOperator);
            changed=true;
        }

        if(!StringUtils.hasText(product.getAggregationType())){
            product.setAggregationType(seed.aggregationType);
            changed=true;
        }

        if(product.getObservationWindowHours()==null){
            product.setObservationWindowHours(seed.observationWindowHours);
            changed=true;
        }

        if(product.getMinimumObservationCount()==null){
            product.setMinimumObservationCount(seed.minimumObservationCount);
            changed=true;
        }

        if(!StringUtils.hasText(product.getTriggerDescription())){
            product.setTriggerDescription(seed.triggerDescription);
            changed=true;
        }

        if(product.getTriggerSyncStatus()==null){
            product.setTriggerSyncStatus(TriggerSyncStatus.PENDING.name());
            changed=true;
        }

        if(!changed){
            log.info("Product {} already has complete trigger configuration",
                    product.getProductCode());
            return Mono.just(product);
        }

        product.setUpdatedAt(LocalDateTime.now());

        return productRepository.save(product)
                .doOnNext(saved->log.info(
                        "Updated missing trigger configuration for product {}",
                        saved.getProductCode()));
    }

    private Mono<Product> createProduct(ProductSeed seed){
        LocalDateTime now=LocalDateTime.now();

        Product product=Product.builder()
                .productCode(seed.productCode)
                .name(seed.name)
                .description(seed.description)
                .category(seed.category)
                .status(ProductStatus.ACTIVE.name())
                .minimumSumInsured(new BigDecimal("10000.00"))
                .maximumSumInsured(new BigDecimal("500000.00"))
                .minimumTermMonths(1)
                .maximumTermMonths(12)
                .eligibilityDescription("Available to eligible registered customers")
                .perilCode(seed.perilCode)
                .measurementType(seed.measurementType)
                .measurementUnit(seed.measurementUnit)
                .thresholdValue(seed.thresholdValue)
                .comparisonOperator(seed.comparisonOperator)
                .aggregationType(seed.aggregationType)
                .observationWindowHours(seed.observationWindowHours)
                .minimumObservationCount(seed.minimumObservationCount)
                .triggerDescription(seed.triggerDescription)
                .triggerSyncStatus(TriggerSyncStatus.PENDING.name())
                .createdBy(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return productRepository.save(product)
                .doOnNext(saved->log.info("Loaded product: {}",saved.getProductCode()));
    }

    private Mono<Coverage> ensureDefaultCoverage(Product product){
        return coverageRepository
                .existsByProductIdAndCoverageCodeIgnoreCase(
                        product.getId(),
                        product.getProductCode()+"-BASE"
                )
                .flatMap(exists->{
                    if(exists){
                        return Mono.empty();
                    }

                    LocalDateTime now=LocalDateTime.now();

                    Coverage coverage=Coverage.builder()
                            .productId(product.getId())
                            .coverageCode(product.getProductCode()+"-BASE")
                            .name("Base Coverage")
                            .description("Mandatory base coverage")
                            .coverageLimit(product.getMaximumSumInsured())
                            .mandatory(true)
                            .active(true)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    return coverageRepository.save(coverage);
                });
    }

    private static final ProductSeed WEATHER_SEED=new ProductSeed(
            "WEATHER-001",
            "Weather Protection Plan",
            "Parametric protection against extreme rainfall and temperature events",
            ProductCategory.WEATHER.name(),
            PerilType.EXCESS_RAINFALL.name(),
            MeasurementType.RAINFALL.name(),
            MeasurementUnit.MM.getDisplay(),
            new BigDecimal("150.00"),
            ComparisonOperator.GREATER_THAN_OR_EQUAL.name(),
            AggregationType.SUM.name(),
            24,
            1,
            "Coverage is triggered when total rainfall reaches or exceeds 150 mm within 24 hours."
    );

    private static final ProductSeed FLOOD_SEED=new ProductSeed(
            "FLOOD-001",
            "Flood Protection Plan",
            "Parametric protection against qualifying flood events",
            ProductCategory.FLOOD.name(),
            PerilType.FLOOD.name(),
            MeasurementType.WATER_LEVEL.name(),
            MeasurementUnit.METER.getDisplay(),
            new BigDecimal("5.00"),
            ComparisonOperator.GREATER_THAN_OR_EQUAL.name(),
            AggregationType.MAX.name(),
            6,
            1,
            "Coverage is triggered when the maximum water level reaches or exceeds 5 m within 6 hours."
    );

    private record ProductSeed(
            String productCode,
            String name,
            String description,
            String category,
            String perilCode,
            String measurementType,
            String measurementUnit,
            BigDecimal thresholdValue,
            String comparisonOperator,
            String aggregationType,
            Integer observationWindowHours,
            Integer minimumObservationCount,
            String triggerDescription
    ){}
}