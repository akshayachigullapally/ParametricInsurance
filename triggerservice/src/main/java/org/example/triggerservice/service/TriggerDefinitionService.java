package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.TriggerDefinitionRequest;
import org.example.triggerservice.dto.TriggerDefinitionResponse;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
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
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.example.triggerservice.util.MeasurementValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerDefinitionService{
    private final TriggerDefinitionRepository triggerDefinitionRepository;
    private final PerilRepository perilRepository;
    private final LocationZoneRepository locationZoneRepository;

    public Flux<TriggerDefinitionResponse> getAll(){
        log.debug("Get all [trigger definition]");
        return triggerDefinitionRepository
                .findAllByStatusNotOrderByDefinitionNameAsc(TriggerDefinitionStatus.ARCHIVED)
                .flatMap(this::buildResponse);
    }

    public Flux<TriggerDefinitionResponse> getActive(){
        log.debug("Get active [trigger definition]");
        return triggerDefinitionRepository
                .findAllByStatusOrderByDefinitionNameAsc(TriggerDefinitionStatus.ACTIVE)
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerDefinitionResponse> getById(Long definitionId){
        log.debug("Get by id [trigger definition]: definitionId={}",definitionId);
        return findAvailableDefinition(definitionId)
                .flatMap(this::buildResponse);
    }

    public Flux<TriggerDefinitionResponse> getByPerilId(Long perilId){
        log.debug("Get by peril id [trigger definition]: perilId={}",perilId);
        validateId(perilId,"Peril ID");

        return perilRepository.findById(perilId)
                .filter(peril->peril.getStatus()!=PerilStatus.ARCHIVED)
                .switchIfEmpty(Mono.error(new NotFoundException("Peril not found")))
                .thenMany(triggerDefinitionRepository
                        .findAllByPerilIdAndStatusNotOrderByDefinitionNameAsc(
                                perilId,
                                TriggerDefinitionStatus.ARCHIVED
                        ))
                .flatMap(this::buildResponse);
    }

    public Flux<TriggerDefinitionResponse> getByLocationZoneId(Long locationZoneId){
        log.debug("Get by location zone id [trigger definition]: locationZoneId={}",locationZoneId);
        validateId(locationZoneId,"Location zone ID");

        return locationZoneRepository.findById(locationZoneId)
                .filter(zone->zone.getStatus()!=LocationZoneStatus.ARCHIVED)
                .switchIfEmpty(Mono.error(new NotFoundException("Location zone not found")))
                .thenMany(triggerDefinitionRepository
                        .findAllByLocationZoneIdAndStatusNotOrderByDefinitionNameAsc(
                                locationZoneId,
                                TriggerDefinitionStatus.ARCHIVED
                        ))
                .flatMap(this::buildResponse);
    }

    public Flux<TriggerDefinitionResponse> getByProductId(Long productId){
        log.debug("Get by product id [trigger definition]: productId={}",productId);
        validateId(productId,"Product ID");

        return triggerDefinitionRepository
                .findAllByProductIdAndStatusNotOrderByDefinitionNameAsc(
                        productId,
                        TriggerDefinitionStatus.ARCHIVED
                )
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerDefinitionResponse> create(
            TriggerDefinitionRequest request,
            Long userId
    ){
        log.info("Create [trigger definition]: userId={}",userId);
        normalize(request);
        ParsedRule parsedRule=parseRule(request);
        validateDates(request.getEffectiveFrom(),request.getEffectiveTo());

        Mono<Boolean> duplicateCodeCheck=
                triggerDefinitionRepository.existsByDefinitionCodeIgnoreCase(
                        request.getDefinitionCode()
                );

        Mono<Boolean> similarDefinitionCheck=
                triggerDefinitionRepository.existsSimilarDefinition(
                        request.getPerilId(),
                        request.getLocationZoneId(),
                        request.getProductId(),
                        parsedRule.measurementType().name()
                );

        return validateReferences(request.getPerilId(),request.getLocationZoneId())
                .then(Mono.zip(duplicateCodeCheck,similarDefinitionCheck))
                .flatMap(checks->{
                    if(checks.getT1()){
                        return Mono.error(
                                new ConflictException("Trigger definition code already exists")
                        );
                    }

                    if(checks.getT2()){
                        return Mono.error(new ConflictException(
                                "A trigger definition already exists for this peril, zone, product and measurement type"
                        ));
                    }

                    LocalDateTime now=LocalDateTime.now();

                    TriggerDefinition definition=TriggerDefinition.builder()
                            .definitionCode(request.getDefinitionCode())
                            .definitionName(request.getDefinitionName())
                            .description(request.getDescription())
                            .perilId(request.getPerilId())
                            .locationZoneId(request.getLocationZoneId())
                            .productId(request.getProductId())
                            .measurementType(parsedRule.measurementType())
                            .measurementUnit(parsedRule.measurementUnit())
                            .aggregationType(parsedRule.aggregationType())
                            .comparisonOperator(parsedRule.comparisonOperator())
                            .thresholdValue(request.getThresholdValue())
                            .observationWindowHours(request.getObservationWindowHours())
                            .minimumObservationCount(request.getMinimumObservationCount())
                            .status(TriggerDefinitionStatus.DRAFT)
                            .effectiveFrom(request.getEffectiveFrom())
                            .effectiveTo(request.getEffectiveTo())
                            .createdBy(userId)
                            .createdAt(now)
                            .updatedBy(userId)
                            .updatedAt(now)
                            .build();

                    return triggerDefinitionRepository.save(definition);
                })
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerDefinitionResponse> update(
            Long definitionId,
            TriggerDefinitionRequest request,
            Long userId
    ){
        log.info("Update [trigger definition]: definitionId={}, userId={}",definitionId,userId);
        normalize(request);
        ParsedRule parsedRule=parseRule(request);
        validateDates(request.getEffectiveFrom(),request.getEffectiveTo());

        return findAvailableDefinition(definitionId)
                .flatMap(definition->{
                    if(definition.getStatus()==TriggerDefinitionStatus.ACTIVE){
                        return Mono.error(new ConflictException(
                                "Deactivate the trigger definition before updating it"
                        ));
                    }

                    Mono<Boolean> duplicateCodeCheck=
                            triggerDefinitionRepository.existsByDefinitionCodeAndIdNot(
                                    request.getDefinitionCode(),
                                    definitionId
                            );

                    Mono<Boolean> similarDefinitionCheck=
                            triggerDefinitionRepository.existsSimilarDefinitionAndIdNot(
                                    request.getPerilId(),
                                    request.getLocationZoneId(),
                                    request.getProductId(),
                                    parsedRule.measurementType().name(),
                                    definitionId
                            );

                    return validateReferences(
                            request.getPerilId(),
                            request.getLocationZoneId()
                    )
                            .then(Mono.zip(duplicateCodeCheck,similarDefinitionCheck))
                            .flatMap(checks->{
                                if(checks.getT1()){
                                    return Mono.error(new ConflictException(
                                            "Trigger definition code already exists"
                                    ));
                                }

                                if(checks.getT2()){
                                    return Mono.error(new ConflictException(
                                            "A trigger definition already exists for this peril, zone, product and measurement type"
                                    ));
                                }

                                definition.setDefinitionCode(request.getDefinitionCode());
                                definition.setDefinitionName(request.getDefinitionName());
                                definition.setDescription(request.getDescription());
                                definition.setPerilId(request.getPerilId());
                                definition.setLocationZoneId(request.getLocationZoneId());
                                definition.setProductId(request.getProductId());
                                definition.setMeasurementType(parsedRule.measurementType());
                                definition.setMeasurementUnit(parsedRule.measurementUnit());
                                definition.setAggregationType(parsedRule.aggregationType());
                                definition.setComparisonOperator(parsedRule.comparisonOperator());
                                definition.setThresholdValue(request.getThresholdValue());
                                definition.setObservationWindowHours(
                                        request.getObservationWindowHours()
                                );
                                definition.setMinimumObservationCount(
                                        request.getMinimumObservationCount()
                                );
                                definition.setEffectiveFrom(request.getEffectiveFrom());
                                definition.setEffectiveTo(request.getEffectiveTo());
                                definition.setUpdatedBy(userId);
                                definition.setUpdatedAt(LocalDateTime.now());

                                return triggerDefinitionRepository.save(definition);
                            });
                })
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerDefinitionResponse> updateStatus(
            Long definitionId,
            String statusValue,
            Long userId
    ){
        log.info("Update status [trigger definition]: definitionId={}, statusValue={}, userId={}",definitionId,statusValue,userId);
        TriggerDefinitionStatus requestedStatus=parseStatus(statusValue);

        if(requestedStatus==TriggerDefinitionStatus.ARCHIVED){
            return Mono.error(new ValidationException(
                    "Use the delete endpoint to archive a trigger definition"
            ));
        }

        if(requestedStatus==TriggerDefinitionStatus.DRAFT){
            return Mono.error(new ValidationException(
                    "A trigger definition cannot be moved back to DRAFT"
            ));
        }

        return findAvailableDefinition(definitionId)
                .flatMap(definition->{
                    if(definition.getStatus()==requestedStatus){
                        return Mono.error(new ConflictException(
                                "Trigger definition is already "+requestedStatus.name()
                        ));
                    }

                    if(requestedStatus==TriggerDefinitionStatus.ACTIVE){
                        return activate(definition,userId);
                    }

                    return deactivate(definition,userId);
                })
                .flatMap(this::buildResponse);
    }

    public Mono<Void> archive(Long definitionId,Long userId){
        log.info("Archive [trigger definition]: definitionId={}, userId={}",definitionId,userId);
        return findAvailableDefinition(definitionId)
                .flatMap(definition->{
                    if(definition.getStatus()==TriggerDefinitionStatus.ACTIVE){
                        return Mono.error(new ConflictException(
                                "Deactivate the trigger definition before archiving it"
                        ));
                    }

                    definition.setStatus(TriggerDefinitionStatus.ARCHIVED);
                    definition.setUpdatedBy(userId);
                    definition.setUpdatedAt(LocalDateTime.now());

                    return triggerDefinitionRepository.save(definition);
                })
                .then();
    }

    private Mono<TriggerDefinition> activate(
            TriggerDefinition definition,
            Long userId
    ){
        if(definition.getStatus()!=TriggerDefinitionStatus.DRAFT&&
                definition.getStatus()!=TriggerDefinitionStatus.INACTIVE){
            return Mono.error(new ConflictException(
                    "Only DRAFT or INACTIVE trigger definitions can be activated"
            ));
        }

        if(definition.getEffectiveTo()!=null&&
                definition.getEffectiveTo().isBefore(LocalDate.now())){
            return Mono.error(new ValidationException(
                    "An expired trigger definition cannot be activated"
            ));
        }

        return validateActiveReferences(
                definition.getPerilId(),
                definition.getLocationZoneId()
        )
                .then(Mono.defer(()->{
                    definition.setStatus(TriggerDefinitionStatus.ACTIVE);
                    definition.setUpdatedBy(userId);
                    definition.setUpdatedAt(LocalDateTime.now());
                    return triggerDefinitionRepository.save(definition);
                }));
    }

    private Mono<TriggerDefinition> deactivate(
            TriggerDefinition definition,
            Long userId
    ){
        if(definition.getStatus()!=TriggerDefinitionStatus.ACTIVE){
            return Mono.error(new ConflictException(
                    "Only an ACTIVE trigger definition can be made INACTIVE"
            ));
        }

        definition.setStatus(TriggerDefinitionStatus.INACTIVE);
        definition.setUpdatedBy(userId);
        definition.setUpdatedAt(LocalDateTime.now());

        return triggerDefinitionRepository.save(definition);
    }

    private Mono<Tuple2<Peril,LocationZone>> validateReferences(
            Long perilId,
            Long locationZoneId
    ){
        Mono<Peril> perilMono=perilRepository.findById(perilId)
                .filter(peril->peril.getStatus()!=PerilStatus.ARCHIVED)
                .switchIfEmpty(Mono.error(new NotFoundException("Peril not found")));

        Mono<LocationZone> zoneMono=locationZoneRepository.findById(locationZoneId)
                .filter(zone->zone.getStatus()!=LocationZoneStatus.ARCHIVED)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Location zone not found")
                ));

        return Mono.zip(perilMono,zoneMono);
    }

    private Mono<Tuple2<Peril,LocationZone>> validateActiveReferences(
            Long perilId,
            Long locationZoneId
    ){
        return validateReferences(perilId,locationZoneId)
                .flatMap(references->{
                    if(references.getT1().getStatus()!=PerilStatus.ACTIVE){
                        return Mono.error(new ConflictException(
                                "The associated peril must be ACTIVE"
                        ));
                    }

                    if(references.getT2().getStatus()!=LocationZoneStatus.ACTIVE){
                        return Mono.error(new ConflictException(
                                "The associated location zone must be ACTIVE"
                        ));
                    }

                    return Mono.just(references);
                });
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

    private ParsedRule parseRule(TriggerDefinitionRequest request){
        MeasurementType measurementType=parseEnum(
                request.getMeasurementType(),
                MeasurementType.class,
                "measurement type"
        );

        MeasurementUnit measurementUnit=parseEnum(
                request.getMeasurementUnit(),
                MeasurementUnit.class,
                "measurement unit"
        );

        AggregationType aggregationType=parseEnum(
                request.getAggregationType(),
                AggregationType.class,
                "aggregation type"
        );

        ComparisonOperator comparisonOperator=parseEnum(
                request.getComparisonOperator(),
                ComparisonOperator.class,
                "comparison operator"
        );

        MeasurementValidator.validateMeasurementUnit(measurementType,measurementUnit);

        return new ParsedRule(
                measurementType,
                measurementUnit,
                aggregationType,
                comparisonOperator
        );
    }

    private TriggerDefinitionStatus parseStatus(String value){
        TriggerDefinitionStatus status=parseEnum(
                value,
                TriggerDefinitionStatus.class,
                "trigger definition status"
        );

        if(status!=TriggerDefinitionStatus.ACTIVE&&
                status!=TriggerDefinitionStatus.INACTIVE&&
                status!=TriggerDefinitionStatus.DRAFT&&
                status!=TriggerDefinitionStatus.ARCHIVED){
            throw new ValidationException("Invalid trigger definition status");
        }

        return status;
    }

    private <T extends Enum<T>> T parseEnum(
            String value,
            Class<T> enumClass,
            String fieldName
    ){
        if(!StringUtils.hasText(value)){
            throw new ValidationException(capitalize(fieldName)+" is required");
        }

        try{
            return Enum.valueOf(
                    enumClass,
                    value.trim().toUpperCase(Locale.ROOT)
            );
        }catch(IllegalArgumentException exception){
            throw new ValidationException("Invalid "+fieldName);
        }
    }

    private void validateDates(LocalDate effectiveFrom,LocalDate effectiveTo){
        if(effectiveFrom==null){
            throw new ValidationException("Effective start date is required");
        }

        if(effectiveTo!=null&&effectiveTo.isBefore(effectiveFrom)){
            throw new ValidationException(
                    "Effective end date cannot be before effective start date"
            );
        }
    }

    private void validateId(Long id,String fieldName){
        if(id==null||id<=0){
            throw new ValidationException(fieldName+" must be greater than zero");
        }
    }

    private void normalize(TriggerDefinitionRequest request){
        request.setDefinitionCode(normalizeCode(request.getDefinitionCode()));
        request.setDefinitionName(normalizeText(request.getDefinitionName()));
        request.setDescription(normalizeText(request.getDescription()));
        request.setMeasurementType(normalizeEnumText(request.getMeasurementType()));
        request.setMeasurementUnit(normalizeEnumText(request.getMeasurementUnit()));
        request.setAggregationType(normalizeEnumText(request.getAggregationType()));
        request.setComparisonOperator(
                normalizeEnumText(request.getComparisonOperator())
        );
    }

    private String normalizeCode(String value){
        if(!StringUtils.hasText(value)){
            return value;
        }

        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+","_")
                .replaceAll("^_+|_+$","");
    }

    private String normalizeText(String value){
        return StringUtils.hasText(value)?value.trim():value;
    }

    private String normalizeEnumText(String value){
        return StringUtils.hasText(value)
                ?value.trim().toUpperCase(Locale.ROOT)
                :value;
    }

    private String capitalize(String value){
        if(!StringUtils.hasText(value)){
            return value;
        }

        return Character.toUpperCase(value.charAt(0))+value.substring(1);
    }

    private record ParsedRule(
            MeasurementType measurementType,
            MeasurementUnit measurementUnit,
            AggregationType aggregationType,
            ComparisonOperator comparisonOperator
    ){}
}