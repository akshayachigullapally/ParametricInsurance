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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name="trigger.seed-data.enabled",havingValue="true",matchIfMissing=true)
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
                .then(loadPeril(
                        "EXTREME_TEMPERATURE",
                        "Extreme Temperature",
                        "Temperature exceeds the configured threshold within the observation period",
                        now
                ))
                .then(loadPeril(
                        "HIGH_WIND",
                        "High Wind",
                        "Wind speed exceeds the configured threshold within the observation period",
                        now
                ))
                .then(loadPeril(
                        "CYCLONE",
                        "Cyclone",
                        "Wind speed exceeds the configured cyclone threshold within the observation period",
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

    private record ZoneSeed(String zoneCode, String zoneName, String state, String district,
            String postalCode, String latitude, String longitude, String radiusKm){}

    private static ZoneSeed zone(String zoneCode, String zoneName, String state, String district,
            String postalCode, String latitude, String longitude, String radiusKm){
        return new ZoneSeed(zoneCode, zoneName, state, district, postalCode, latitude, longitude, radiusKm);
    }

    private static final List<ZoneSeed> ZONE_SEEDS=List.of(
            // Metro city zones
            zone("HYDERABAD_CENTRAL", "Hyderabad Central Zone", "Telangana", "Hyderabad", "500001", "17.385044", "78.486671", "25.00"),
            zone("BANGALORE_SOUTH", "Bangalore South Zone", "Karnataka", "Bangalore", "560001", "12.971599", "77.594566", "30.00"),
            zone("MUMBAI_WEST", "Mumbai West Zone", "Maharashtra", "Mumbai", "400001", "19.075984", "72.877656", "35.00"),
            zone("DELHI_NORTH", "Delhi North Zone", "Delhi", "New Delhi", "110001", "28.704060", "77.102493", "20.00"),
            zone("CHENNAI_EAST", "Chennai East Zone", "Tamil Nadu", "Chennai", "600001", "13.082680", "80.270718", "28.00"),
            // Hyderabad localities
            zone("HYD_GACHIBOWLI", "Hyderabad Gachibowli Zone", "Telangana", "Hyderabad", "500032", "17.440081", "78.348915", "5.00"),
            zone("HYD_HITEC_CITY", "Hyderabad HITEC City Zone", "Telangana", "Hyderabad", "500081", "17.448293", "78.391487", "5.00"),
            zone("HYD_KONDAPUR", "Hyderabad Kondapur Zone", "Telangana", "Hyderabad", "500084", "17.469900", "78.357700", "4.00"),
            zone("HYD_MANIKONDA", "Hyderabad Manikonda Zone", "Telangana", "Hyderabad", "500089", "17.405000", "78.386000", "4.00"),
            zone("HYD_KUKATPALLY", "Hyderabad Kukatpally Zone", "Telangana", "Hyderabad", "500072", "17.494793", "78.399644", "5.00"),
            zone("HYD_MIYAPUR", "Hyderabad Miyapur Zone", "Telangana", "Hyderabad", "500049", "17.496800", "78.361400", "5.00"),
            zone("HYD_JUBILEE_HILLS", "Hyderabad Jubilee Hills Zone", "Telangana", "Hyderabad", "500033", "17.432600", "78.407100", "4.00"),
            zone("HYD_BANJARA_HILLS", "Hyderabad Banjara Hills Zone", "Telangana", "Hyderabad", "500034", "17.415600", "78.434700", "4.00"),
            zone("HYD_AMEERPET", "Hyderabad Ameerpet Zone", "Telangana", "Hyderabad", "500038", "17.437462", "78.448288", "3.00"),
            zone("HYD_BEGUMPET", "Hyderabad Begumpet Zone", "Telangana", "Hyderabad", "500016", "17.444700", "78.466400", "3.00"),
            zone("HYD_SECUNDERABAD", "Hyderabad Secunderabad Zone", "Telangana", "Hyderabad", "500003", "17.439930", "78.498276", "6.00"),
            zone("HYD_MALKAJGIRI", "Hyderabad Malkajgiri Zone", "Telangana", "Hyderabad", "500047", "17.447400", "78.526300", "4.00"),
            zone("HYD_UPPAL", "Hyderabad Uppal Zone", "Telangana", "Hyderabad", "500039", "17.405800", "78.559100", "5.00"),
            zone("HYD_KOMPALLY", "Hyderabad Kompally Zone", "Telangana", "Hyderabad", "500014", "17.536200", "78.486800", "5.00"),
            zone("HYD_MEHDIPATNAM", "Hyderabad Mehdipatnam Zone", "Telangana", "Hyderabad", "500028", "17.395900", "78.431200", "4.00"),
            zone("HYD_CHARMINAR", "Hyderabad Charminar Old City Zone", "Telangana", "Hyderabad", "500002", "17.361564", "78.474665", "5.00"),
            zone("HYD_DILSUKHNAGAR", "Hyderabad Dilsukhnagar Zone", "Telangana", "Hyderabad", "500060", "17.368800", "78.524700", "4.00"),
            zone("HYD_LB_NAGAR", "Hyderabad LB Nagar Zone", "Telangana", "Hyderabad", "500074", "17.345700", "78.552200", "5.00"),
            zone("HYD_SHAMSHABAD", "Hyderabad Shamshabad Zone", "Telangana", "Hyderabad", "501218", "17.260300", "78.396900", "8.00"),
            // Other Indian cities
            zone("WARANGAL_CENTRAL", "Warangal Central Zone", "Telangana", "Warangal", "506002", "17.968901", "79.594055", "15.00"),
            zone("KARIMNAGAR_CENTRAL", "Karimnagar Central Zone", "Telangana", "Karimnagar", "505001", "18.438555", "79.128841", "12.00"),
            zone("NIZAMABAD_CENTRAL", "Nizamabad Central Zone", "Telangana", "Nizamabad", "503001", "18.672505", "78.094087", "12.00"),
            zone("VISAKHAPATNAM_CENTRAL", "Visakhapatnam Central Zone", "Andhra Pradesh", "Visakhapatnam", "530001", "17.686816", "83.218482", "20.00"),
            zone("VIJAYAWADA_CENTRAL", "Vijayawada Central Zone", "Andhra Pradesh", "NTR", "520001", "16.506174", "80.648015", "15.00"),
            zone("TIRUPATI_CENTRAL", "Tirupati Central Zone", "Andhra Pradesh", "Tirupati", "517501", "13.628756", "79.419182", "12.00"),
            zone("MYSURU_CENTRAL", "Mysuru Central Zone", "Karnataka", "Mysuru", "570001", "12.295810", "76.639381", "15.00"),
            zone("MANGALURU_CENTRAL", "Mangaluru Central Zone", "Karnataka", "Dakshina Kannada", "575001", "12.914142", "74.855957", "15.00"),
            zone("PUNE_CENTRAL", "Pune Central Zone", "Maharashtra", "Pune", "411001", "18.520430", "73.856743", "25.00"),
            zone("NAGPUR_CENTRAL", "Nagpur Central Zone", "Maharashtra", "Nagpur", "440001", "21.145800", "79.088155", "20.00"),
            zone("AHMEDABAD_CENTRAL", "Ahmedabad Central Zone", "Gujarat", "Ahmedabad", "380001", "23.022505", "72.571362", "25.00"),
            zone("SURAT_CENTRAL", "Surat Central Zone", "Gujarat", "Surat", "395003", "21.170240", "72.831061", "20.00"),
            zone("KOLKATA_CENTRAL", "Kolkata Central Zone", "West Bengal", "Kolkata", "700001", "22.572646", "88.363895", "25.00"),
            zone("JAIPUR_CENTRAL", "Jaipur Central Zone", "Rajasthan", "Jaipur", "302001", "26.912434", "75.787271", "20.00"),
            zone("LUCKNOW_CENTRAL", "Lucknow Central Zone", "Uttar Pradesh", "Lucknow", "226001", "26.846694", "80.946166", "20.00"),
            zone("KOCHI_CENTRAL", "Kochi Central Zone", "Kerala", "Ernakulam", "682001", "9.931233", "76.267304", "15.00"),
            zone("THIRUVANANTHAPURAM_CENTRAL", "Thiruvananthapuram Central Zone", "Kerala", "Thiruvananthapuram", "695001", "8.524139", "76.936638", "15.00"),
            zone("COIMBATORE_CENTRAL", "Coimbatore Central Zone", "Tamil Nadu", "Coimbatore", "641001", "11.016844", "76.955832", "18.00"),
            zone("MADURAI_CENTRAL", "Madurai Central Zone", "Tamil Nadu", "Madurai", "625001", "9.925201", "78.119775", "15.00"),
            zone("BHUBANESWAR_CENTRAL", "Bhubaneswar Central Zone", "Odisha", "Khordha", "751001", "20.296059", "85.824539", "18.00"),
            zone("GUWAHATI_CENTRAL", "Guwahati Central Zone", "Assam", "Kamrup Metropolitan", "781001", "26.144517", "91.736237", "18.00"),
            zone("PATNA_CENTRAL", "Patna Central Zone", "Bihar", "Patna", "800001", "25.594095", "85.137566", "18.00"),
            zone("CHANDIGARH_CENTRAL", "Chandigarh Central Zone", "Chandigarh", "Chandigarh", "160017", "30.733315", "76.779419", "12.00"),
            zone("INDORE_CENTRAL", "Indore Central Zone", "Madhya Pradesh", "Indore", "452001", "22.719568", "75.857727", "18.00"),
            zone("BHOPAL_CENTRAL", "Bhopal Central Zone", "Madhya Pradesh", "Bhopal", "462001", "23.259933", "77.412615", "18.00"),
            zone("PANAJI_CENTRAL", "Panaji Central Zone", "Goa", "North Goa", "403001", "15.490930", "73.827850", "10.00")
    );

    private Mono<Void> loadLocationZone(LocalDateTime now){
        // Sequential so the R2DBC pool is not flooded and zone ids are assigned in a stable order.
        return Flux.fromIterable(ZONE_SEEDS)
                .concatMap(seed->loadZone(seed.zoneCode(), seed.zoneName(), seed.state(), seed.district(),
                        seed.postalCode(), new BigDecimal(seed.latitude()), new BigDecimal(seed.longitude()),
                        new BigDecimal(seed.radiusKm()), now))
                .then();
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