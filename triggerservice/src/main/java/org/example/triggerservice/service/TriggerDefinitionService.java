package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.TriggerDefinitionResponse;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.Peril;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.PerilRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerDefinitionService{
    private final TriggerDefinitionRepository triggerDefinitionRepository;
    private final PerilRepository perilRepository;
    private final LocationZoneRepository locationZoneRepository;

    public Mono<TriggerDefinitionResponse> getById(Long definitionId){
        log.debug("Get by id [trigger definition]: definitionId={}",definitionId);
        return findAvailableDefinition(definitionId)
                .flatMap(this::buildResponse);
    }

    private Mono<TriggerDefinition> findAvailableDefinition(Long definitionId){
        validateId(definitionId,"Trigger definition ID");

        return triggerDefinitionRepository.findById(definitionId)
                .filter(definition->
                        definition.getStatus()!=TriggerDefinitionStatus.ARCHIVED
                )
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Trigger definition not found")
                ));
    }

    private Mono<TriggerDefinitionResponse> buildResponse(
            TriggerDefinition definition
    ){
        Mono<Peril> perilMono=perilRepository.findById(definition.getPerilId())
                .defaultIfEmpty(Peril.builder()
                        .id(definition.getPerilId())
                        .perilCode("UNAVAILABLE")
                        .perilName("Unavailable")
                        .build());

        Mono<LocationZone> zoneMono=locationZoneRepository
                .findById(definition.getLocationZoneId())
                .defaultIfEmpty(LocationZone.builder()
                        .id(definition.getLocationZoneId())
                        .zoneCode("UNAVAILABLE")
                        .zoneName("Unavailable")
                        .build());

        return Mono.zip(perilMono,zoneMono)
                .map(tuple->toResponse(definition,tuple.getT1(),tuple.getT2()));
    }

    private TriggerDefinitionResponse toResponse(
            TriggerDefinition definition,
            Peril peril,
            LocationZone zone
    ){
        return TriggerDefinitionResponse.builder()
                .id(definition.getId())
                .definitionCode(definition.getDefinitionCode())
                .definitionName(definition.getDefinitionName())
                .description(definition.getDescription())
                .perilId(definition.getPerilId())
                .perilCode(peril.getPerilCode())
                .perilName(peril.getPerilName())
                .locationZoneId(definition.getLocationZoneId())
                .zoneCode(zone.getZoneCode())
                .zoneName(zone.getZoneName())
                .productId(definition.getProductId())
                .measurementType(definition.getMeasurementType())
                .measurementUnit(definition.getMeasurementUnit())
                .aggregationType(definition.getAggregationType())
                .comparisonOperator(definition.getComparisonOperator())
                .thresholdValue(definition.getThresholdValue())
                .observationWindowHours(definition.getObservationWindowHours())
                .minimumObservationCount(definition.getMinimumObservationCount())
                .status(definition.getStatus())
                .effectiveFrom(definition.getEffectiveFrom())
                .effectiveTo(definition.getEffectiveTo())
                .createdBy(definition.getCreatedBy())
                .createdAt(definition.getCreatedAt())
                .updatedBy(definition.getUpdatedBy())
                .updatedAt(definition.getUpdatedAt())
                .build();
    }

    private void validateId(Long id,String fieldName){
        if(id==null||id<=0){
            throw new ValidationException(fieldName+" must be greater than zero");
        }
    }
}
