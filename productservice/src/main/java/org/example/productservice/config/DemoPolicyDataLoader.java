package org.example.productservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.productservice.model.Policy;
import org.example.productservice.model.PolicyTriggerSnapshot;
import org.example.productservice.model.Product;
import org.example.productservice.repository.PolicyRepository;
import org.example.productservice.repository.PolicyTriggerSnapshotRepository;
import org.example.productservice.repository.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoPolicyDataLoader implements ApplicationRunner{
    private static final Long SYSTEM_USER_ID=1L;
    private static final Long HYDERABAD_CENTRAL_ZONE_ID=1L;

    private final ProductRepository productRepository;
    private final PolicyRepository policyRepository;
    private final PolicyTriggerSnapshotRepository snapshotRepository;

    @Override
    public void run(ApplicationArguments args){
        Mono.when(
                loadPolicy(1L,"WEATHER-001",HYDERABAD_CENTRAL_ZONE_ID,
                        new BigDecimal("150000.00")),
                loadPolicy(2L,"FLOOD-001",HYDERABAD_CENTRAL_ZONE_ID,
                        new BigDecimal("250000.00"))
        ).doOnSuccess(ignored->
                log.info("Demo policy initialization completed"))
                .doOnError(error->
                        log.error("Demo policy initialization failed",error))
                .subscribe();
    }

    private Mono<Void> loadPolicy(
            Long customerId,
            String productCode,
            Long zoneId,
            BigDecimal sumInsured
    ){
        return productRepository.findByProductCodeIgnoreCase(productCode)
                .filter(this::hasCompleteTriggerConfiguration)
                .flatMap(product->policyRepository
                        .existsByCustomerIdAndProductIdAndStatus(
                                customerId,
                                product.getId(),
                                "ACTIVE"
                        )
                        .flatMap(exists->{
                            if(exists){
                                log.info("Demo policy already exists for customer {} on product {}",
                                        customerId,productCode);
                                return Mono.empty();
                            }
                            return createPolicy(customerId,product,zoneId,sumInsured);
                        }))
                .then();
    }

    private Mono<Policy> createPolicy(
            Long customerId,
            Product product,
            Long zoneId,
            BigDecimal sumInsured
    ){
        LocalDateTime now=LocalDateTime.now();

        Policy policy=Policy.builder()
                .policyNumber(generatePolicyNumber(product.getProductCode()))
                .customerId(customerId)
                .productId(product.getId())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .sumInsured(sumInsured)
                .premiumAmount(sumInsured
                        .multiply(new BigDecimal("0.0200"))
                        .setScale(2,RoundingMode.HALF_UP))
                .termMonths(12)
                .startDate(LocalDate.now().minusMonths(2))
                .endDate(LocalDate.now().plusMonths(10))
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .locationZoneId(zoneId)
                .build();

        return policyRepository.save(policy)
                .flatMap(saved->createSnapshot(saved,product,now)
                        .flatMap(snapshot->{
                            saved.setTriggerSnapshotId(snapshot.getId());
                            saved.setUpdatedAt(now);
                            return policyRepository.save(saved);
                        }))
                .doOnNext(saved->log.info(
                        "Loaded demo policy {} for product {}",
                        saved.getPolicyNumber(),
                        product.getProductCode()));
    }

    private Mono<PolicyTriggerSnapshot> createSnapshot(
            Policy policy,
            Product product,
            LocalDateTime now
    ){
        PolicyTriggerSnapshot snapshot=PolicyTriggerSnapshot.builder()
                .policyId(policy.getId())
                .productId(product.getId())
                .productCode(product.getProductCode())
                .perilCode(product.getPerilCode())
                .measurementType(product.getMeasurementType())
                .measurementUnit(product.getMeasurementUnit())
                .thresholdValue(product.getThresholdValue())
                .comparisonOperator(product.getComparisonOperator())
                .aggregationType(product.getAggregationType())
                .observationWindowHours(product.getObservationWindowHours())
                .minimumObservationCount(product.getMinimumObservationCount())
                .triggerDescription(product.getTriggerDescription())
                .capturedAt(now)
                .createdAt(now)
                .build();

        return snapshotRepository.save(snapshot);
    }

    private boolean hasCompleteTriggerConfiguration(Product product){
        return product!=null&&
                StringUtils.hasText(product.getMeasurementType())&&
                product.getThresholdValue()!=null&&
                StringUtils.hasText(product.getComparisonOperator());
    }

    private String generatePolicyNumber(String productCode){
        return "DEMO-"+productCode+"-"+UUID.randomUUID()
                .toString().replace("-","")
                .substring(0,6)
                .toUpperCase(Locale.ROOT);
    }
}