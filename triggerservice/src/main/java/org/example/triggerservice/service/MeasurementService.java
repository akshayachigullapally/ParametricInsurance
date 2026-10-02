package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.MeasurementCurrentResponse;
import org.example.triggerservice.dto.ManualReadingRequest;
import org.example.triggerservice.dto.ManualReadingResponse;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.ObservationSource;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.dto.MeasurementTypeInfo;
import org.example.triggerservice.dto.CurrentMeasurementRequest;
import org.example.triggerservice.dto.CurrentMeasurementResult;
import org.example.triggerservice.dto.InternalProductConfig;
import org.example.triggerservice.dto.ActivePolicyForTriggerResponse;
import org.example.triggerservice.client.ProductTriggerClient;
import org.example.triggerservice.client.ClaimsServiceClient;
import org.example.triggerservice.dto.ClaimCreationRequest;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.CurrentMeasurement;
import org.example.triggerservice.model.TriggerEvent;
import org.example.triggerservice.model.TriggerEventStatus;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.example.triggerservice.repository.CurrentMeasurementRepository;
import org.example.triggerservice.repository.TriggerEventRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.example.triggerservice.repository.PerilRepository;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasurementService{
    private final ObservationRepository observationRepository;
    private final LocationZoneRepository locationZoneRepository;
    private final CurrentMeasurementRepository currentMeasurementRepository;
    private final ProductTriggerClient productTriggerClient;
    private final ClaimsServiceClient claimsServiceClient;
    private final IdempotencyService idempotencyService;
    private final TriggerEventRepository triggerEventRepository;
    private final NotificationService notificationService;
    private final TriggerDefinitionRepository triggerDefinitionRepository;
    private final PerilRepository perilRepository;

    public Flux<MeasurementTypeInfo> getMeasurementTypes(){
        log.debug("Get measurement types");
        return Flux.fromArray(MeasurementType.values())
                .map(type->MeasurementTypeInfo.builder()
                        .measurementType(type)
                        .measurementUnit(unitFor(type))
                        .displayName(displayName(type))
                        .build());
    }

    public Mono<CurrentMeasurementResult> processCurrentMeasurement(
            CurrentMeasurementRequest request,Long recordedBy){
        log.info("Process current measurement: recordedBy={}",recordedBy);
        LocalDateTime now=LocalDateTime.now();
        return idempotencyService.isDuplicate(request.getIdempotencyKey())
            .flatMap(duplicate->{
                if(duplicate){
                    return idempotencyService.resourceId(request.getIdempotencyKey())
                        .flatMap(resourceId->triggerEventRepository.findById(resourceId)
                            .map(this::resultFromEvent))
                        .switchIfEmpty(Mono.just(CurrentMeasurementResult.builder()
                            .matched(false)
                            .productId(request.getProductId())
                            .locationZoneId(request.getLocationZoneId())
                            .currentValue(request.getCurrentValue())
                            .message("This idempotent measurement request was already processed.")
                            .build()));
                }
                return processNewMeasurement(request,recordedBy,now)
                        .flatMap(result->notificationService.audit(
                                "MEASUREMENT_RECORDED","PRODUCT",result.getProductId(),recordedBy,
                                "Recorded current measurement "+result.getCurrentValue()+
                                        " for product "+result.getProductCode()+
                                        " in zone "+result.getLocationZoneId()+
                                        (result.getTriggerEventReference()!=null
                                                ?" (created trigger event "+result.getTriggerEventReference()+")"
                                                :" (no trigger)")
                        ).thenReturn(result))
                        .flatMap(result->{
                        if(result.getTriggerEventId()==null){
                            return idempotencyService.record(request.getIdempotencyKey(),"CURRENT_MEASUREMENT_NO_MATCH",null)
                                .thenReturn(result);
                        }
                        return idempotencyService.record(request.getIdempotencyKey(),"CURRENT_MEASUREMENT",result.getTriggerEventId())
                            .thenReturn(result);
                        });
            });
        }

        private Mono<CurrentMeasurementResult> processNewMeasurement(
            CurrentMeasurementRequest request,Long recordedBy,LocalDateTime now){
        return productTriggerClient.getProduct(request.getProductId())
                .switchIfEmpty(Mono.<InternalProductConfig>error(new NotFoundException("Product not found")))
                .<CurrentMeasurementResult>flatMap(product->validateProduct(product)
                        .then(locationZoneRepository.findById(request.getLocationZoneId())
                                .switchIfEmpty(Mono.<LocationZone>error(new NotFoundException("Location zone not found")))
                                .flatMap(zone->{
                                    if(zone.getStatus()!=org.example.triggerservice.model.LocationZoneStatus.ACTIVE){
                                        return Mono.<CurrentMeasurementResult>error(new ValidationException("Location zone is not ACTIVE"));
                                    }

                                    return currentMeasurementRepository
                                            .findByProductIdAndLocationZoneId(request.getProductId(),request.getLocationZoneId())
                                            .defaultIfEmpty(CurrentMeasurement.builder()
                                                    .productId(request.getProductId())
                                                    .locationZoneId(request.getLocationZoneId())
                                                    .build())
                                            .flatMap(current->{
                                                current.setMeasurementType(product.getMeasurementType());
                                                current.setMeasurementUnit(product.getMeasurementUnit());
                                                current.setCurrentValue(request.getCurrentValue());
                                                current.setRecordedBy(recordedBy);
                                                current.setRecordedAt(now);
                                                current.setUpdatedAt(now);
                                                return currentMeasurementRepository.save(current)
                                                    .then(productTriggerClient.getActivePolicies(
                                                        request.getProductId(),request.getLocationZoneId())
                                                        .collectList())
                                                    .flatMap(policies->buildProcessingResult(
                                                            product,request,recordedBy,policies));
                                            });
                                })));
    }

        private Mono<CurrentMeasurementResult> buildProcessingResult(
            InternalProductConfig product,CurrentMeasurementRequest request,Long recordedBy,
            java.util.List<ActivePolicyForTriggerResponse> policies){
        java.util.List<ActivePolicyForTriggerResponse> thresholdMatched=policies.stream()
            .filter(policy->policy.getTriggerSnapshot()!=null)
            .filter(policy->compare(request.getCurrentValue(),policy.getTriggerSnapshot().getThresholdValue(),policy.getTriggerSnapshot().getComparisonOperator()))
            .toList();
        // Only policies with a paid premium are covered; the rest are reported, not claimed.
        java.util.List<ActivePolicyForTriggerResponse> matchedPolicies=thresholdMatched.stream()
            .filter(policy->Boolean.TRUE.equals(policy.getPremiumPaid()))
            .toList();
        long matched=matchedPolicies.size();
        int unpaid=thresholdMatched.size()-matchedPolicies.size();
        String unpaidNote=unpaid==0?"":" "+unpaid+" policy(ies) crossed the threshold but were skipped because the premium is unpaid.";
        CurrentMeasurementResult base=CurrentMeasurementResult.builder()
            .matched(matched>0).productId(product.getProductId()).productCode(product.getProductCode())
            .productName(product.getProductName()).locationZoneId(request.getLocationZoneId())
            .measurementType(product.getMeasurementType()).measurementUnit(product.getMeasurementUnit())
            .currentValue(request.getCurrentValue()).currentProductThresholdValue(product.getThresholdValue())
            .comparisonOperator(product.getComparisonOperator()).activePolicyCount(policies.size())
            .matchedPolicyCount((int)matched).unpaidPolicyCount(unpaid).build();
        if(matched==0){
            base.setMessage(unpaid==0
                ?"The current measurement did not match any active policy trigger."
                :"No automatic claim was created."+unpaidNote);
            return Mono.just(base);
        }
        LocalDateTime now=LocalDateTime.now();
        int windowHours=product.getObservationWindowHours()!=null&&product.getObservationWindowHours()>0
            ?product.getObservationWindowHours():24;
        LocalDateTime windowStart=now.minusHours(windowHours);
        MeasurementType resolvedType=resolveMeasurementType(product.getMeasurementType());
        TriggerEvent event=TriggerEvent.builder().eventReference("TEV-"+UUID.randomUUID())
            .productId(product.getProductId()).locationZoneId(request.getLocationZoneId())
            .measurementType(resolvedType)
            .measurementUnit(unitFor(resolvedType))
            .aggregatedValue(request.getCurrentValue()).thresholdValue(product.getThresholdValue())
            .windowStart(windowStart).windowEnd(now)
            .status(TriggerEventStatus.VERIFIED).detectedAt(now).recordedBy(recordedBy)
            .matchedPolicyCount((int)matched).claimsCreatedCount(0).notificationsCreatedCount(0)
            .idempotencyKey(request.getIdempotencyKey()).build();
        return linkDefinitionAndPeril(event,product).then(triggerEventRepository.save(event)).flatMap(saved->
            claimsServiceClient.createClaimsFromTriggerEvent(ClaimCreationRequest.builder()
                .triggerEventId(saved.getId()).triggerEventReference(saved.getEventReference())
                .triggerDefinitionId(saved.getTriggerDefinitionId()).perilId(saved.getPerilId())
                .productId(product.getProductId()).locationZoneId(request.getLocationZoneId())
                .measurementType(product.getMeasurementType()).measurementUnit(product.getMeasurementUnit())
                .measuredValue(request.getCurrentValue()).thresholdValue(product.getThresholdValue())
                .comparisonOperator(product.getComparisonOperator())
                .eventWindowStart(windowStart).eventWindowEnd(now)
                .eligiblePolicies(matchedPolicies.stream().map(policy->ClaimCreationRequest.EligiblePolicyInfo.builder()
                    .policyId(policy.getPolicyId()).policyNumber(policy.getPolicyNumber()).customerId(policy.getCustomerId()).sumInsured(policy.getSumInsured()).build()).toList())
                .build()).flatMap(response->{
                    saved.setClaimsCreatedCount(response.getClaimsCreated());
                    saved.setNotificationsCreatedCount(response.getClaimsCreated());
                    saved.setProcessedAt(LocalDateTime.now());
                        base.setTriggerEventId(saved.getId());
                        base.setTriggerEventReference(saved.getEventReference());
                        base.setTriggerEventStatus(saved.getStatus().name());
                        base.setClaimsCreatedCount(response.getClaimsCreated());
                        base.setNotificationsCreatedCount(response.getClaimsCreated());
                        base.setMessage("The current measurement matched "+matched+" active policy snapshot(s) and automatic claims were created."+unpaidNote);
                        return triggerEventRepository.save(saved).thenReturn(base);
                }));
        }

    /**
     * Records which trigger definition and peril the event belongs to, so the claims it
     * opens can show them. Uses the product's definition for this zone and measurement;
     * when none exists (e.g. a product without synced definitions) only the peril is
     * looked up by the product's peril code.
     */
    private Mono<Void> linkDefinitionAndPeril(TriggerEvent event,InternalProductConfig product){
        Mono<Void> byDefinition=triggerDefinitionRepository
                .findByProductIdAndMeasurementTypeAndLocationZoneIdAndStatusNot(
                        event.getProductId(),event.getMeasurementType(),
                        event.getLocationZoneId(),TriggerDefinitionStatus.ARCHIVED)
                .doOnNext(definition->{
                    event.setTriggerDefinitionId(definition.getId());
                    event.setPerilId(definition.getPerilId());
                })
                .then();

        Mono<Void> byPerilCode=Mono.defer(()->event.getPerilId()!=null||product.getPerilCode()==null
                ?Mono.<Void>empty()
                :perilRepository.findByPerilCodeIgnoreCase(product.getPerilCode())
                        .doOnNext(peril->event.setPerilId(peril.getId()))
                        .then());

        // Missing links must never block the claims themselves.
        return byDefinition.then(byPerilCode)
                .onErrorResume(error->{
                    log.warn("Could not link trigger definition/peril for product {}: {}",
                            event.getProductId(),error.getMessage());
                    return Mono.empty();
                });
    }

        private CurrentMeasurementResult resultFromEvent(TriggerEvent event){
        return CurrentMeasurementResult.builder().matched(true).productId(event.getProductId())
            .locationZoneId(event.getLocationZoneId()).currentValue(event.getAggregatedValue())
            .currentProductThresholdValue(event.getThresholdValue()).triggerEventId(event.getId())
            .triggerEventReference(event.getEventReference()).triggerEventStatus(event.getStatus().name())
            .matchedPolicyCount(event.getMatchedPolicyCount()).claimsCreatedCount(event.getClaimsCreatedCount())
            .notificationsCreatedCount(event.getNotificationsCreatedCount())
            .message("This idempotent measurement request returned the existing trigger event.").build();
        }

    private Mono<Void> validateProduct(InternalProductConfig product){
        if(!"ACTIVE".equalsIgnoreCase(product.getStatus())){
            return Mono.error(new ValidationException("Product is not ACTIVE"));
        }
        if(product.getThresholdValue()==null||product.getComparisonOperator()==null||
                product.getMeasurementType()==null||product.getMeasurementUnit()==null){
            return Mono.error(new ValidationException("Product trigger configuration is incomplete"));
        }
        try{
            resolveMeasurementType(product.getMeasurementType());
        }catch(IllegalArgumentException exception){
            return Mono.error(new ValidationException(
                    "Product measurement type '"+product.getMeasurementType()
                            +"' is not supported by the trigger engine"));
        }
        return Mono.empty();
    }

    /**
     * productservice stores measurement type as a free-form string that may use a
     * different vocabulary than this service's own {@link MeasurementType} enum
     * (e.g. productservice's "WATER_LEVEL" corresponds to this service's
     * {@code RIVER_LEVEL}). Normalize known aliases before parsing.
     */
    private MeasurementType resolveMeasurementType(String rawMeasurementType){
        String normalized=rawMeasurementType==null?null:rawMeasurementType.trim().toUpperCase();
        if("WATER_LEVEL".equals(normalized)){
            normalized="RIVER_LEVEL";
        }
        return MeasurementType.valueOf(normalized);
    }

    private boolean compare(java.math.BigDecimal current,java.math.BigDecimal threshold,String operator){
        if(current==null||threshold==null||operator==null){
            return false;
        }
        int comparison=current.compareTo(threshold);
        return switch(operator){
            case "GREATER_THAN"->comparison>0;
            case "GREATER_THAN_OR_EQUAL"->comparison>=0;
            case "LESS_THAN"->comparison<0;
            case "LESS_THAN_OR_EQUAL"->comparison<=0;
            case "EQUAL"->comparison==0;
            default->false;
        };
    }

    public Flux<org.example.triggerservice.dto.CurrentMeasurementSnapshotResponse> getCurrentMeasurementSnapshots(){
        log.debug("Get current measurement snapshots");
        return currentMeasurementRepository.findAll()
                .map(current->org.example.triggerservice.dto.CurrentMeasurementSnapshotResponse.builder()
                        .productId(current.getProductId())
                        .locationZoneId(current.getLocationZoneId())
                        .measurementType(current.getMeasurementType())
                        .measurementUnit(current.getMeasurementUnit())
                        .currentValue(current.getCurrentValue())
                        .recordedAt(current.getRecordedAt())
                        .updatedAt(current.getUpdatedAt())
                        .build());
    }

    public Flux<MeasurementCurrentResponse> getCurrentMeasurements(){
        log.debug("Get current measurements");
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

    /**
     * Sets the live reading of one measurement type in every active zone. The reading is
     * stored as a validated MANUAL observation, so the dashboard and the monitor treat it
     * like any other reading, and the demo feed leaves it alone for {@code holdMinutes}.
     */
    public Mono<ManualReadingResponse> recordManualReading(
            ManualReadingRequest request,
            Long userId,
            int holdMinutes
    ){
        if(request==null||request.getMeasurementType()==null||request.getMeasurementType().isBlank()){
            return Mono.error(new ValidationException("Measurement type is required"));
        }
        if(request.getValue()==null){
            return Mono.error(new ValidationException("Value is required"));
        }

        MeasurementType type;
        try{
            type=MeasurementType.valueOf(request.getMeasurementType().trim().toUpperCase(java.util.Locale.ROOT));
        }catch(IllegalArgumentException exception){
            return Mono.error(new ValidationException("Unknown measurement type: "+request.getMeasurementType()));
        }
        if(type==MeasurementType.OTHER){
            return Mono.error(new ValidationException("This measurement type has no live reading"));
        }

        java.math.BigDecimal value=request.getValue();
        if(type!=MeasurementType.TEMPERATURE&&value.signum()<0){
            return Mono.error(new ValidationException("Value cannot be negative"));
        }
        if((type==MeasurementType.HUMIDITY||type==MeasurementType.SOIL_MOISTURE)
                &&value.compareTo(java.math.BigDecimal.valueOf(100))>0){
            return Mono.error(new ValidationException("A percentage cannot be more than 100"));
        }

        LocalDateTime now=LocalDateTime.now();
        MeasurementUnit unit=unitFor(type);

        return latestValidatedFor(type)
                .map(Observation::getObservedValue)
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(previous->locationZoneRepository
                        .findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE)
                        .flatMap(zone->observationRepository.save(Observation.builder()
                                .observationReference("OBS-"+now.getYear()+"-"+shortId())
                                .locationZoneId(zone.getId())
                                .measurementType(type)
                                .measurementUnit(unit)
                                .observedValue(value)
                                .observedAt(now)
                                .source(ObservationSource.MANUAL)
                                .sourceReference("ADMIN-"+shortId())
                                .status(ObservationStatus.VALIDATED)
                                .receivedBy(userId)
                                .receivedAt(now)
                                .build()))
                        .count()
                        .flatMap(zoneCount->{
                            if(zoneCount==0){
                                return Mono.error(new ValidationException(
                                        "There are no active zones to record the reading for"));
                            }
                            String label=type.name().replace('_',' ').toLowerCase(java.util.Locale.ROOT);
                            return notificationService.auditChange(
                                            "MEASUREMENT_VALUE_UPDATED","MEASUREMENT",null,userId,
                                            "Set current "+label+" to "+value.toPlainString()+" in "+zoneCount+" zone(s)",
                                            previous.map(java.math.BigDecimal::toPlainString).orElse(null),
                                            value.toPlainString())
                                    .onErrorResume(error->Mono.empty())
                                    .thenReturn(ManualReadingResponse.builder()
                                            .measurementType(type)
                                            .measurementUnit(unit)
                                            .value(value)
                                            .previousValue(previous.orElse(null))
                                            .observedAt(now)
                                            .zoneCount(zoneCount.intValue())
                                            .holdMinutes(holdMinutes)
                                            .build());
                        }));
    }

    private static String shortId(){
        return UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(java.util.Locale.ROOT);
    }

    private Mono<Observation> latestValidatedFor(
            MeasurementType measurementType
    ){
        return observationRepository.findLatestValidated(measurementType.name());
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
                .source(observation.getSource())
                .build();
    }

    private MeasurementUnit unitFor(MeasurementType measurementType){
        return switch(measurementType){
            case RAINFALL->MeasurementUnit.MM;
            case RIVER_LEVEL->MeasurementUnit.METER;
            case TEMPERATURE->MeasurementUnit.CELSIUS;
            case WIND_SPEED->MeasurementUnit.KM_PER_HOUR;
            case HUMIDITY,SOIL_MOISTURE->MeasurementUnit.PERCENTAGE;
            case OTHER->MeasurementUnit.OTHER;
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
            case OTHER->"Other";
        };
    }
}