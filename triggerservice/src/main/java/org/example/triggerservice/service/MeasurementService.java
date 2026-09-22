package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.MeasurementCurrentResponse;
import org.example.triggerservice.dto.MeasurementTypeInfo;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MeasurementService{
    private final ObservationRepository observationRepository;
    private final LocationZoneRepository locationZoneRepository;

    public Flux<MeasurementTypeInfo> getMeasurementTypes(){
        return Flux.fromArray(MeasurementType.values())
                .map(type->MeasurementTypeInfo.builder()
                        .measurementType(type)
                        .measurementUnit(unitFor(type))
                        .displayName(displayName(type))
                        .build());
    }

    public Flux<MeasurementCurrentResponse> getCurrentMeasurements(){
        return Flux.fromArray(MeasurementType.values())
                .flatMap(this::latestValidatedFor)
                .flatMap(observation->locationZoneRepository
                        .findById(observation.getLocationZoneId())
                        .defaultIfEmpty(LocationZone.builder()
                                .id(observation.getLocationZoneId())
                                .zoneCode("UNAVAILABLE")
                                .zoneName("Unavailable")
                                .build())
                        .map(zone->toResponse(observation,zone)));
    }

    private Mono<Observation> latestValidatedFor(
            MeasurementType measurementType
    ){
        return observationRepository
                .findAllByMeasurementTypeAndStatusOrderByObservedAtDesc(
                        measurementType,
                        ObservationStatus.VALIDATED)
                .next();
    }

    private MeasurementCurrentResponse toResponse(
            Observation observation,
            LocationZone zone
    ){
        return MeasurementCurrentResponse.builder()
                .measurementType(observation.getMeasurementType())
                .measurementUnit(observation.getMeasurementUnit())
                .currentValue(observation.getObservedValue())
                .observedAt(observation.getObservedAt())
                .locationZoneId(zone.getId())
                .zoneCode(zone.getZoneCode())
                .zoneName(zone.getZoneName())
                .status(observation.getStatus())
                .build();
    }

    private MeasurementUnit unitFor(MeasurementType measurementType){
        return switch(measurementType){
            case RAINFALL->MeasurementUnit.MM;
            case RIVER_LEVEL->MeasurementUnit.METER;
            case TEMPERATURE->MeasurementUnit.CELSIUS;
            case WIND_SPEED->MeasurementUnit.KM_PER_HOUR;
            case HUMIDITY,SOIL_MOISTURE->MeasurementUnit.PERCENTAGE;
        };
    }

    private String displayName(MeasurementType measurementType){
        return switch(measurementType){
            case RAINFALL->"Rainfall";
            case RIVER_LEVEL->"River Level";
            case TEMPERATURE->"Temperature";
            case WIND_SPEED->"Wind Speed";
            case HUMIDITY->"Humidity";
            case SOIL_MOISTURE->"Soil Moisture";
        };
    }
}