package org.example.triggerservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.model.AggregationType;
import org.example.triggerservice.model.ComparisonOperator;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.Peril;
import org.example.triggerservice.model.PerilStatus;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.PerilRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerDataLoader implements CommandLineRunner{
    private static final Long SYSTEM_USER_ID=1L;

    private final PerilRepository perilRepository;
    private final LocationZoneRepository locationZoneRepository;
    private final TriggerDefinitionRepository triggerDefinitionRepository;

    @Override
    public void run(String... args){
        LocalDateTime now=LocalDateTime.now();

loadPeril(
                        "EXCESS_RAINFALL",
                        "Excess Rainfall",
                        "Rainfall exceeds the configured threshold within the observation period",
                        now
                )
                .then(loadPeril(
                        "DROUGHT",
                        "Drought",
                        "Recorded rainfall remains below the configured minimum threshold",
                        now
                ))
                .then(loadPeril(
                        "FLOOD",
                        "Flood",
                        "River level exceeds the configured threshold within the observation period",
                        now
                ))
                .then(loadLocationZone(now))
                .then(loadTriggerDefinition(now))
                .then(loadDemoTriggerDefinitions(now))
                .doOnSuccess(unused->
                        log.info("Trigger service initial data loading completed"))
                .doOnError(error->
                        log.error("Trigger service initial data loading failed: {}",
                                error.getMessage(),error))
                .subscribe();
    }

    private Mono<Peril> loadPeril(
            String perilCode,
            String perilName,
            String description,
            LocalDateTime now
    ){
        return perilRepository.findByPerilCodeIgnoreCase(perilCode)
                .switchIfEmpty(Mono.defer(()->perilRepository.save(
                        Peril.builder()
                                .perilCode(perilCode)
                                .perilName(perilName)
                                .description(description)
                                .status(PerilStatus.ACTIVE)
                                .createdBy(SYSTEM_USER_ID)
                                .createdAt(now)
                                .updatedBy(SYSTEM_USER_ID)
                                .updatedAt(now)
                                .build()
                ).doOnNext(peril->
                        log.info("Loaded peril: {}",peril.getPerilCode()))));
    }

    private Mono<Void> loadLocationZone(LocalDateTime now){
        return Mono.when(
                loadZone("HYDERABAD_CENTRAL", "Hyderabad Central Zone", "Telangana", "Hyderabad", "500001",
                        new BigDecimal("17.385044"), new BigDecimal("78.486671"), new BigDecimal("25.00"), now),
                loadZone("BANGALORE_SOUTH", "Bangalore South Zone", "Karnataka", "Bangalore", "560001",
                        new BigDecimal("12.971599"), new BigDecimal("77.594566"), new BigDecimal("30.00"), now),
                loadZone("MUMBAI_WEST", "Mumbai West Zone", "Maharashtra", "Mumbai", "400001",
                        new BigDecimal("19.075984"), new BigDecimal("72.877656"), new BigDecimal("35.00"), now),
                loadZone("DELHI_NORTH", "Delhi North Zone", "Delhi", "New Delhi", "110001",
                        new BigDecimal("28.704060"), new BigDecimal("77.102493"), new BigDecimal("20.00"), now),
                loadZone("CHENNAI_EAST", "Chennai East Zone", "Tamil Nadu", "Chennai", "600001",
                        new BigDecimal("13.082680"), new BigDecimal("80.270718"), new BigDecimal("28.00"), now)
        ).then();
    }

    private Mono<LocationZone> loadZone(String zoneCode, String zoneName, String state, String district,
            String postalCode, BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm, LocalDateTime now){
        return locationZoneRepository
                .findByZoneCodeIgnoreCase(zoneCode)
                .switchIfEmpty(Mono.defer(()->locationZoneRepository.save(
                        LocationZone.builder()
                                .zoneCode(zoneCode)
                                .zoneName(zoneName)
                                .country("India")
                                .state(state)
                                .district(district)
                                .postalCode(postalCode)
                                .latitude(latitude)
                                .longitude(longitude)
                                .radiusKm(radiusKm)
                                .status(LocationZoneStatus.ACTIVE)
                                .createdBy(SYSTEM_USER_ID)
                                .createdAt(now)
                                .updatedBy(SYSTEM_USER_ID)
                                .updatedAt(now)
                                .build()
                ).doOnNext(zone->
                        log.info("Loaded location zone: {}",zone.getZoneCode()))));
    }

    private Mono<TriggerDefinition> loadTriggerDefinition(LocalDateTime now){
        return triggerDefinitionRepository
                .findByDefinitionCodeIgnoreCase("HYD_RAIN_150MM_24H")
                .switchIfEmpty(Mono.defer(()->Mono.zip(
                                perilRepository.findByPerilCodeIgnoreCase(
                                        "EXCESS_RAINFALL"),
                                locationZoneRepository.findByZoneCodeIgnoreCase(
                                        "HYDERABAD_CENTRAL")
                        )
                        .switchIfEmpty(Mono.error(new IllegalStateException(
                                "Peril or location zone loader data is unavailable")))
                        .flatMap(tuple->triggerDefinitionRepository.save(
                                TriggerDefinition.builder()
                                        .definitionCode("HYD_RAIN_150MM_24H")
                                        .definitionName(
                                                "Hyderabad Excess Rainfall Trigger")
                                        .description(
                                                "Activates when cumulative rainfall reaches 150 mm in 24 hours")
                                        .perilId(tuple.getT1().getId())
                                        .locationZoneId(tuple.getT2().getId())
                                        .productId(1L)
                                        .measurementType(MeasurementType.RAINFALL)
                                        .measurementUnit(MeasurementUnit.MM)
                                        .aggregationType(AggregationType.SUM)
                                        .comparisonOperator(
                                                ComparisonOperator.GREATER_THAN_OR_EQUAL)
                                        .thresholdValue(new BigDecimal("150.00"))
                                        .observationWindowHours(24)
                                        .minimumObservationCount(1)
                                        .status(TriggerDefinitionStatus.DRAFT)
                                        .effectiveFrom(LocalDate.now())
                                        .effectiveTo(LocalDate.now().plusYears(1))
                                        .createdBy(SYSTEM_USER_ID)
                                        .createdAt(now)
                                        .updatedBy(SYSTEM_USER_ID)
                                        .updatedAt(now)
                                        .build()
                        ))
                        .doOnNext(definition->
                                log.info("Loaded trigger definition: {}",
                                        definition.getDefinitionCode()))));
    }

    private Mono<Void> loadDemoTriggerDefinitions(LocalDateTime now){
        return locationZoneRepository
                .findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE)
                .flatMap(zone->Mono.when(
                        loadDemoDefinition(
                                zone,
                                1L,
                                "P1_"+zone.getZoneCode()+"_RAINFALL",
                                "WEATHER-001 Rainfall Trigger – "+zone.getZoneName(),
                                "Rainfall exceeds 150 mm over the last 24 hours",
                                "EXCESS_RAINFALL",
                                MeasurementType.RAINFALL,
                                MeasurementUnit.MM,
                                AggregationType.SUM,
                                ComparisonOperator.GREATER_THAN_OR_EQUAL,
                                "150.00",
                                24,
                                1,
                                now),
                        loadDemoDefinition(
                                zone,
                                2L,
                                "P2_"+zone.getZoneCode()+"_RIVER_LEVEL",
                                "FLOOD-001 River Level Trigger – "+zone.getZoneName(),
                                "River level reaches 5 m within the last 6 hours",
                                "FLOOD",
                                MeasurementType.RIVER_LEVEL,
                                MeasurementUnit.METER,
                                AggregationType.MAXIMUM,
                                ComparisonOperator.GREATER_THAN_OR_EQUAL,
                                "5.00",
                                6,
                                1,
                                now)
                ))
                .then();
    }

    private Mono<TriggerDefinition> loadDemoDefinition(
            LocationZone zone,
            Long productId,
            String definitionCode,
            String definitionName,
            String description,
            String perilCode,
            MeasurementType measurementType,
            MeasurementUnit measurementUnit,
            AggregationType aggregationType,
            ComparisonOperator comparisonOperator,
            String thresholdValue,
            int windowHours,
            int minObservationCount,
            LocalDateTime now
    ){
        return triggerDefinitionRepository
                .findByProductIdAndMeasurementTypeAndLocationZoneIdAndStatusNot(
                        productId,
                        measurementType,
                        zone.getId(),
                        TriggerDefinitionStatus.ARCHIVED)
                .switchIfEmpty(Mono.defer(()->perilRepository
                        .findByPerilCodeIgnoreCase(perilCode)
                        .switchIfEmpty(Mono.error(new IllegalStateException(
                                "Peril loader data is unavailable: "+perilCode)))
                        .flatMap(peril->triggerDefinitionRepository.save(
                                TriggerDefinition.builder()
                                        .definitionCode(definitionCode)
                                        .definitionName(definitionName)
                                        .description(description)
                                        .perilId(peril.getId())
                                        .locationZoneId(zone.getId())
                                        .productId(productId)
                                        .measurementType(measurementType)
                                        .measurementUnit(measurementUnit)
                                        .aggregationType(aggregationType)
                                        .comparisonOperator(comparisonOperator)
                                        .thresholdValue(new BigDecimal(thresholdValue))
                                        .observationWindowHours(windowHours)
                                        .minimumObservationCount(minObservationCount)
                                        .status(TriggerDefinitionStatus.ACTIVE)
                                        .effectiveFrom(LocalDate.now())
                                        .effectiveTo(LocalDate.now().plusYears(1))
                                        .createdBy(SYSTEM_USER_ID)
                                        .createdAt(now)
                                        .updatedBy(SYSTEM_USER_ID)
                                        .updatedAt(now)
                                        .build()
                        )))
                        .map(definition->{
                            log.info("Loaded trigger definition: {}",
                                    definition.getDefinitionCode());
                            return definition;
                        }));
    }
}