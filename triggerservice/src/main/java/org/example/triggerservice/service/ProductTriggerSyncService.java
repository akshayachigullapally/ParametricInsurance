package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.dto.ProductTriggerSyncRequest;
import org.example.triggerservice.dto.ProductTriggerSyncResult;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.AggregationType;
import org.example.triggerservice.model.ComparisonOperator;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.Peril;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.PerilRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.example.triggerservice.util.MeasurementValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductTriggerSyncService{
    private static final Long SYSTEM_USER_ID=1L;

    private final PerilRepository perilRepository;
    private final LocationZoneRepository locationZoneRepository;
    private final TriggerDefinitionRepository triggerDefinitionRepository;

    public Mono<ProductTriggerSyncResult> syncProductDefinition(
            Long productId,
            ProductTriggerSyncRequest request
    ){
        LocalDateTime now=LocalDateTime.now();

        validateRequest(productId,request);

        MeasurementType measurementType=normalizeMeasurementType(
                request.getMeasurementType());
        if(measurementType==null){
            throw new ValidationException(
                    "Unsupported measurement type: "+request.getMeasurementType());
        }

        MeasurementUnit measurementUnit=normalizeMeasurementUnit(
                request.getMeasurementUnit());
        if(measurementUnit==null){
            throw new ValidationException(
                    "Unsupported measurement unit: "+request.getMeasurementUnit());
        }

        MeasurementValidator.validateMeasurementUnit(
                measurementType,measurementUnit);

        AggregationType aggregationType=normalizeAggregationType(
                request.getAggregationType());
        if(aggregationType==null){
            throw new ValidationException(
                    "Unsupported aggregation type: "+request.getAggregationType());
        }

        ComparisonOperator comparisonOperator=parseComparisonOperator(
                request.getComparisonOperator());

        boolean productActive="ACTIVE".equalsIgnoreCase(
                request.getProductStatus());

        return perilRepository.findByPerilCodeIgnoreCase(
                        request.getPerilCode())
                .switchIfEmpty(Mono.error(new ValidationException(
                        "Peril not found: "+request.getPerilCode())))
                .flatMapMany(peril->productActive
                        ? locationZoneRepository
                                .findAllByStatusOrderByZoneNameAsc(
                                        LocationZoneStatus.ACTIVE)
                                .flatMap(zone->upsertDefinition(
                                        productId,request,peril,zone,
                                        measurementType,measurementUnit,
                                        aggregationType,comparisonOperator,now))
                        : triggerDefinitionRepository
                                .findAllByProductIdAndStatusNot(
                                        productId,
                                        TriggerDefinitionStatus.ARCHIVED)
                                .flatMap(definition->deactivateDefinition(
                                        definition,
                                        request.getUpdatedBy(),
                                        now)))
                .collectList()
                .map(updated->buildResult(
                        productId,request,productActive,updated.size()));
    }

    private void validateRequest(
            Long productId,
            ProductTriggerSyncRequest request
    ){
        if(request==null){
            throw new ValidationException("Sync payload is required");
        }
        if(productId==null||
                request.getProductId()==null||
                !productId.equals(request.getProductId())){
            throw new ValidationException(
                    "Product id in path must match payload productId");
        }
        if(!StringUtils.hasText(request.getProductCode())){
            throw new ValidationException("Product code is required");
        }
        if(!StringUtils.hasText(request.getPerilCode())){
            throw new ValidationException("Peril code is required");
        }
        if(!StringUtils.hasText(request.getMeasurementType())){
            throw new ValidationException("Measurement type is required");
        }
        if(request.getThresholdValue()==null||
                request.getThresholdValue().compareTo(BigDecimal.ZERO)<=0){
            throw new ValidationException(
                    "Threshold value must be greater than zero");
        }
        if(request.getObservationWindowHours()==null||
                request.getObservationWindowHours()<=0){
            throw new ValidationException(
                    "Observation window hours must be positive");
        }
        if(request.getMinimumObservationCount()==null||
                request.getMinimumObservationCount()<1){
            throw new ValidationException(
                    "Minimum observation count must be at least 1");
        }
    }

    private Mono<TriggerDefinition> upsertDefinition(
            Long productId,
            ProductTriggerSyncRequest request,
            Peril peril,
            LocationZone zone,
            MeasurementType measurementType,
            MeasurementUnit measurementUnit,
            AggregationType aggregationType,
            ComparisonOperator comparisonOperator,
            LocalDateTime now
    ){
        String definitionCode=buildDefinitionCode(
                productId,zone,measurementType);

        return triggerDefinitionRepository
                .findByProductIdAndMeasurementTypeAndLocationZoneIdAndStatusNot(
                        productId,
                        measurementType,
                        zone.getId(),
                        TriggerDefinitionStatus.ARCHIVED)
                .flatMap(existing->{
                    applyDefinitionValues(
                            existing,request,peril,zone,
                            measurementType,measurementUnit,
                            aggregationType,comparisonOperator,
                            request.getUpdatedBy(),now);
                    if(existing.getStatus()!=TriggerDefinitionStatus.ACTIVE){
                        existing.setStatus(TriggerDefinitionStatus.ACTIVE);
                    }
                    return triggerDefinitionRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(()->triggerDefinitionRepository
                        .findByDefinitionCodeIgnoreCase(definitionCode)
                        .flatMap(previous->{
                            applyDefinitionValues(
                                    previous,request,peril,zone,
                                    measurementType,measurementUnit,
                                    aggregationType,comparisonOperator,
                                    request.getUpdatedBy(),now);
                            if(previous.getStatus()==
                                    TriggerDefinitionStatus.ARCHIVED){
                                previous.setStatus(
                                        TriggerDefinitionStatus.ACTIVE);
                            }
                            return triggerDefinitionRepository.save(previous);
                        })
                        .switchIfEmpty(Mono.defer(()->{
                            TriggerDefinition fresh=buildNewDefinition(
                                    productId,request,peril,zone,
                                    definitionCode,measurementType,
                                    measurementUnit,aggregationType,
                                    comparisonOperator,now);
                            return triggerDefinitionRepository.save(fresh);
                        }))))
                .doOnNext(definition->log.info(
                        "Synchronized trigger definition {} (status {}) for product {}",
                        definition.getDefinitionCode(),
                        definition.getStatus(),
                        productId));
    }

    private Mono<TriggerDefinition> deactivateDefinition(
            TriggerDefinition definition,
            Long updatedBy,
            LocalDateTime now
    ){
        if(definition.getStatus()!=TriggerDefinitionStatus.INACTIVE){
            definition.setStatus(TriggerDefinitionStatus.INACTIVE);
            definition.setUpdatedBy(
                    updatedBy!=null?updatedBy:SYSTEM_USER_ID);
            definition.setUpdatedAt(now);
            return triggerDefinitionRepository.save(definition);
        }
        return Mono.just(definition);
    }

    private void applyDefinitionValues(
            TriggerDefinition definition,
            ProductTriggerSyncRequest request,
            Peril peril,
            LocationZone zone,
            MeasurementType measurementType,
            MeasurementUnit measurementUnit,
            AggregationType aggregationType,
            ComparisonOperator comparisonOperator,
            Long updatedBy,
            LocalDateTime now
    ){
        definition.setDefinitionName(
                request.getProductName()+" – "+zone.getZoneName()+" ("
                +displayLabel(measurementType)+")");
        definition.setDescription(
                normalizeDescription(request.getTriggerDescription(),
                        measurementType));
        definition.setPerilId(peril.getId());
        definition.setLocationZoneId(zone.getId());
        definition.setProductId(request.getProductId());
        definition.setMeasurementType(measurementType);
        definition.setMeasurementUnit(measurementUnit);
        definition.setAggregationType(aggregationType);
        definition.setComparisonOperator(comparisonOperator);
        definition.setThresholdValue(request.getThresholdValue());
        definition.setObservationWindowHours(
                request.getObservationWindowHours());
        definition.setMinimumObservationCount(
                request.getMinimumObservationCount());
        definition.setStatus(TriggerDefinitionStatus.ACTIVE);
        if(definition.getEffectiveFrom()==null){
            definition.setEffectiveFrom(LocalDate.now());
        }
        definition.setEffectiveTo(LocalDate.now().plusYears(1));
        definition.setUpdatedBy(updatedBy!=null?updatedBy:SYSTEM_USER_ID);
        definition.setUpdatedAt(now);
    }

    private TriggerDefinition buildNewDefinition(
            Long productId,
            ProductTriggerSyncRequest request,
            Peril peril,
            LocationZone zone,
            String definitionCode,
            MeasurementType measurementType,
            MeasurementUnit measurementUnit,
            AggregationType aggregationType,
            ComparisonOperator comparisonOperator,
            LocalDateTime now
    ){
        Long userId=request.getUpdatedBy()!=null
                ?request.getUpdatedBy():SYSTEM_USER_ID;

        return TriggerDefinition.builder()
                .definitionCode(definitionCode)
                .definitionName(request.getProductName()+" – "
                        +zone.getZoneName()+" ("
                        +displayLabel(measurementType)+")")
                .description(normalizeDescription(
                        request.getTriggerDescription(),measurementType))
                .perilId(peril.getId())
                .locationZoneId(zone.getId())
                .productId(productId)
                .measurementType(measurementType)
                .measurementUnit(measurementUnit)
                .aggregationType(aggregationType)
                .comparisonOperator(comparisonOperator)
                .thresholdValue(request.getThresholdValue())
                .observationWindowHours(request.getObservationWindowHours())
                .minimumObservationCount(
                        request.getMinimumObservationCount())
                .status(TriggerDefinitionStatus.ACTIVE)
                .effectiveFrom(LocalDate.now())
                .effectiveTo(LocalDate.now().plusYears(1))
                .createdBy(userId)
                .createdAt(now)
                .updatedBy(userId)
                .updatedAt(now)
                .build();
    }

    private ProductTriggerSyncResult buildResult(
            Long productId,
            ProductTriggerSyncRequest request,
            boolean productActive,
            int updated
    ){
        String message;
        int synced;
        int deactivated;

        if(productActive){
            synced=updated;
            deactivated=0;
            message="Synchronized "+synced
                    +" active trigger definition(s) across active zones";
        }else{
            synced=0;
            deactivated=updated;
            message="Deactivated "+deactivated
                    +" trigger definition(s) for inactive product";
        }

        return ProductTriggerSyncResult.builder()
                .productId(productId)
                .productCode(request.getProductCode())
                .productActive(productActive)
                .definitionsSynced(synced)
                .definitionsDeactivated(deactivated)
                .message(message)
                .build();
    }

    private String buildDefinitionCode(
            Long productId,
            LocationZone zone,
            MeasurementType measurementType
    ){
        return "P"+productId+"_"+zone.getZoneCode()+"_"
                +measurementType.name();
    }

    private MeasurementType normalizeMeasurementType(String value){
        if(!StringUtils.hasText(value)){
            return null;
        }
        String normalized=value.trim().toUpperCase();
        return switch(normalized){
            case "WATER_LEVEL"->MeasurementType.RIVER_LEVEL;
            case "RAINFALL","TEMPERATURE","WIND_SPEED","HUMIDITY",
                    "SOIL_MOISTURE","RIVER_LEVEL"->
                    MeasurementType.valueOf(normalized);
            default->null;
        };
    }

    private MeasurementUnit normalizeMeasurementUnit(String value){
        if(!StringUtils.hasText(value)){
            return null;
        }
        String normalized=value.trim().toUpperCase(java.util.Locale.ROOT)
                .replace("KM/H","KM_PER_HOUR")
                .replace("%","PERCENTAGE")
                .replace("°","");
        return switch(normalized){
            case "M","METER"->MeasurementUnit.METER;
            case "MM","MILLIMETER"->MeasurementUnit.MM;
            case "C","CELSIUS"->MeasurementUnit.CELSIUS;
            case "KM_PER_HOUR"->MeasurementUnit.KM_PER_HOUR;
            case "PERCENTAGE","PCT"->MeasurementUnit.PERCENTAGE;
            default->null;
        };
    }

    private AggregationType normalizeAggregationType(String value){
        if(!StringUtils.hasText(value)){
            return null;
        }
        String normalized=value.trim().toUpperCase();
        return switch(normalized){
            case "MIN"->AggregationType.MINIMUM;
            case "MAX"->AggregationType.MAXIMUM;
            case "SUM","AVERAGE","LATEST"->AggregationType.valueOf(normalized);
            default->null;
        };
    }

    private ComparisonOperator parseComparisonOperator(String value){
        if(!StringUtils.hasText(value)){
            throw new ValidationException(
                    "Comparison operator is required");
        }
        try{
            return ComparisonOperator.valueOf(value.trim().toUpperCase());
        }catch(IllegalArgumentException exception){
            throw new ValidationException(
                    "Unsupported comparison operator: "+value);
        }
    }

    private String normalizeDescription(
            String description,
            MeasurementType measurementType
    ){
        if(!StringUtils.hasText(description)){
            return "Automatic trigger definition derived from product "
                    + "threshold for "+displayLabel(measurementType);
        }
        return description.trim();
    }

    private String displayLabel(MeasurementType measurementType){
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