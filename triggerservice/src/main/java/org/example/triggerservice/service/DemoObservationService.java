package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.client.ClaimsServiceClient;
import org.example.triggerservice.client.InternalNotificationClient;
import org.example.triggerservice.client.ProductTriggerClient;
import org.example.triggerservice.dto.ClaimCreationRequest;
import org.example.triggerservice.dto.ClaimCreationResponse;
import org.example.triggerservice.dto.DemoObservationRequest;
import org.example.triggerservice.dto.DemoObservationResponse;
import org.example.triggerservice.dto.EligiblePolicyResponse;
import org.example.triggerservice.dto.InternalNotificationRequest;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationSource;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.example.triggerservice.model.TriggerEvent;
import org.example.triggerservice.model.TriggerEventStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.example.triggerservice.repository.TriggerEventRepository;
import org.example.triggerservice.util.MeasurementValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoObservationService{
    private static final Long SYSTEM_USER_ID=1L;

    private final ObservationRepository observationRepository;
    private final TriggerDefinitionRepository triggerDefinitionRepository;
    private final TriggerEventRepository triggerEventRepository;
    private final LocationZoneRepository locationZoneRepository;
    private final ObservationValidationService observationValidationService;
    private final TriggerEvaluationService triggerEvaluationService;
    private final ProductTriggerClient productTriggerClient;
    private final ClaimsServiceClient claimsServiceClient;
    private final InternalNotificationClient notificationServiceClient;

    public Mono<DemoObservationResponse> recordAndEvaluate(
            DemoObservationRequest request,
            Long userId
    ){
        log.info("Record and evaluate [demo observation]: userId={}",userId);
        validateRequest(request);

        return triggerDefinitionRepository.findById(request.getTriggerDefinitionId())
                .switchIfEmpty(Mono.error(new NotFoundException("Trigger definition not found")))
                .flatMap(definition->{
                    validateDefinitionMatchesRequest(definition,request);

                    if(definition.getStatus()!=TriggerDefinitionStatus.ACTIVE){
                        return Mono.error(new ConflictException(
                                "Trigger definition is not ACTIVE"));
                    }

                    return recordObservation(definition,request,userId)
                            .flatMap(observation->{
                                if(request.isValidateImmediately()){
                                    return validateObservation(observation,userId);
                                }
                                return Mono.just(observation);
                            })
                            .flatMap(observation->{
                                if(request.isEvaluateImmediately()){
                                    return evaluateTrigger(definition,observation,userId,request);
                                }
                                return buildResponse(observation,null,null,null,null,0,"Observation recorded successfully");
                            });
                });
    }

    private void validateRequest(DemoObservationRequest request){
        if(request==null){
            throw new ValidationException("Request is required");
        }
        if(request.getProductId()==null||request.getProductId()<=0){
            throw new ValidationException("Product ID is required");
        }
        if(request.getTriggerDefinitionId()==null||request.getTriggerDefinitionId()<=0){
            throw new ValidationException("Trigger definition ID is required");
        }
        if(request.getLocationZoneId()==null||request.getLocationZoneId()<=0){
            throw new ValidationException("Location zone ID is required");
        }
        if(!StringUtils.hasText(request.getMeasurementType())){
            throw new ValidationException("Measurement type is required");
        }
        if(!StringUtils.hasText(request.getMeasurementUnit())){
            throw new ValidationException("Measurement unit is required");
        }
        if(request.getObservedValue()==null){
            throw new ValidationException("Observed value is required");
        }
        if(request.getObservedAt()==null){
            throw new ValidationException("Observation time is required");
        }
        if(request.getObservedAt().isAfter(LocalDateTime.now())){
            throw new ValidationException("Observation time cannot be in the future");
        }
    }

    private void validateDefinitionMatchesRequest(
            TriggerDefinition definition,
            DemoObservationRequest request
    ){
        if(!definition.getProductId().equals(request.getProductId())){
            throw new ValidationException("Trigger definition does not belong to the specified product");
        }
        if(!definition.getLocationZoneId().equals(request.getLocationZoneId())){
            throw new ValidationException("Trigger definition does not belong to the specified location zone");
        }
        if(!definition.getMeasurementType().name().equalsIgnoreCase(request.getMeasurementType())){
            throw new ValidationException("Measurement type does not match trigger definition");
        }
        if(!definition.getMeasurementUnit().name().equalsIgnoreCase(request.getMeasurementUnit().replace("_",""))){
            String defUnit=definition.getMeasurementUnit().name();
            String reqUnit=request.getMeasurementUnit().toUpperCase(Locale.ROOT).replace("_","");
            if(!defUnit.equals(reqUnit)){
                throw new ValidationException("Measurement unit does not match trigger definition");
            }
        }
    }

    private Mono<Observation> recordObservation(
            TriggerDefinition definition,
            DemoObservationRequest request,
            Long userId
    ){
        return locationZoneRepository.findById(request.getLocationZoneId())
                .switchIfEmpty(Mono.error(new NotFoundException("Location zone not found")))
                .flatMap(zone->{
                    if(zone.getStatus()!=LocationZoneStatus.ACTIVE){
                        return Mono.error(new ConflictException("Location zone is not ACTIVE"));
                    }

                    MeasurementType measurementType=MeasurementType.valueOf(
                            request.getMeasurementType().trim().toUpperCase(Locale.ROOT));
                    MeasurementUnit measurementUnit=MeasurementUnit.valueOf(
                            request.getMeasurementUnit().trim().toUpperCase(Locale.ROOT).replace("_",""));

                    MeasurementValidator.validateMeasurementUnit(measurementType,measurementUnit);

                    String sourceRef=request.getSourceReference()!=null
                            ?request.getSourceReference()
                            :"ADMIN-DEMO-"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(Locale.ROOT);

                    return observationRepository.existsBySourceReference(sourceRef)
                            .flatMap(exists->{
                                if(exists){
                                    return Mono.error(new ConflictException(
                                            "An observation with this source reference already exists"));
                                }

                                Observation observation=Observation.builder()
                                        .observationReference(generateReference())
                                        .locationZoneId(request.getLocationZoneId())
                                        .measurementType(measurementType)
                                        .measurementUnit(measurementUnit)
                                        .observedValue(request.getObservedValue())
                                        .observedAt(request.getObservedAt())
                                        .source(ObservationSource.MANUAL)
                                        .sourceReference(sourceRef)
                                        .status(ObservationStatus.RECEIVED)
                                        .receivedBy(userId!=null?userId:SYSTEM_USER_ID)
                                        .receivedAt(LocalDateTime.now())
                                        .build();

                                return observationRepository.save(observation);
                            });
                });
    }

    private Mono<Observation> validateObservation(
            Observation observation,
            Long userId
    ){
        return observationValidationService.validateObservation(observation.getId(),userId)
                .then(observationRepository.findById(observation.getId()));
    }

    private Mono<DemoObservationResponse> evaluateTrigger(
            TriggerDefinition definition,
            Observation observation,
            Long userId,
            DemoObservationRequest request
    ){
        return triggerEvaluationService.evaluate(definition.getId(),null,userId)
                .flatMap(evaluationResponse->{
                    if(evaluationResponse.getResult()!=org.example.triggerservice.model.TriggerEvaluationResult.MATCHED){
                        return buildResponse(
                                observation,
                                evaluationResponse,
                                null,
                                null,
                                evaluationResponse.getResult().name(),
                                0,
                                "Evaluation completed: "+evaluationResponse.getResult().name());
                    }

                    return createEventIfRequired(definition,evaluationResponse)
                            .flatMap(event->{
                                if(request.isAutoVerifyMatchedEvent()){
                                    return autoVerifyEvent(event,userId)
                                            .flatMap(verifiedEvent->processVerifiedEvent(verifiedEvent,observation,evaluationResponse,request));
                                }
                                return buildResponse(
                                        observation,
                                        evaluationResponse,
                                        event,
                                        event.getStatus().name(),
                                        "MATCHED",
                                        0,
                                        "Evaluation MATCHED, event created with status: "+event.getStatus().name());
                            });
                });
    }

    private Mono<TriggerEvent> createEventIfRequired(
            TriggerDefinition definition,
            org.example.triggerservice.dto.TriggerEvaluationResponse evaluationResponse
    ){
        // A MATCHED evaluation has already created its event; reuse it instead of
        // inserting a second event for the same evaluation.
        if(evaluationResponse.getTriggerEventId()!=null){
            return triggerEventRepository.findById(evaluationResponse.getTriggerEventId());
        }
        return triggerEventRepository
                .existsByTriggerDefinitionIdAndWindowStartAndWindowEndAndStatusNot(
                        definition.getId(),
                        evaluationResponse.getWindowStart(),
                        evaluationResponse.getWindowEnd(),
                        TriggerEventStatus.REJECTED)
                .flatMap(exists->{
                    if(exists){
                        return triggerEventRepository
                                .findByTriggerDefinitionIdAndWindowStartAndWindowEnd(
                                        definition.getId(),
                                        evaluationResponse.getWindowStart(),
                                        evaluationResponse.getWindowEnd());
                    }

                    TriggerEvent event=TriggerEvent.builder()
                            .eventReference(generateEventReference())
                            .triggerDefinitionId(definition.getId())
                            .triggerEvaluationId(evaluationResponse.getId())
                            .perilId(definition.getPerilId())
                            .locationZoneId(definition.getLocationZoneId())
                            .productId(definition.getProductId())
                            .measurementType(definition.getMeasurementType())
                            .measurementUnit(definition.getMeasurementUnit())
                            .aggregatedValue(evaluationResponse.getAggregatedValue())
                            .thresholdValue(definition.getThresholdValue())
                            .windowStart(evaluationResponse.getWindowStart())
                            .windowEnd(evaluationResponse.getWindowEnd())
                            .status(TriggerEventStatus.DETECTED)
                            .detectedAt(LocalDateTime.now())
                            .build();

                    return triggerEventRepository.save(event);
                });
    }

    private Mono<TriggerEvent> autoVerifyEvent(
            TriggerEvent event,
            Long userId
    ){
        if(event.getStatus()!=TriggerEventStatus.DETECTED){
            return Mono.just(event);
        }

        event.setStatus(TriggerEventStatus.VERIFIED);
        event.setReviewedBy(userId!=null?userId:SYSTEM_USER_ID);
        event.setReviewedAt(LocalDateTime.now());
        event.setReviewNotes("Automatically verified through administrator demonstration workflow");

        return triggerEventRepository.save(event);
    }

    private Mono<DemoObservationResponse> processVerifiedEvent(
            TriggerEvent event,
            Observation observation,
            org.example.triggerservice.dto.TriggerEvaluationResponse evaluationResponse,
            DemoObservationRequest request
    ){
        return productTriggerClient.getEligiblePoliciesForEvent(
                        event.getProductId(),
                        event.getLocationZoneId(),
                        event.getWindowStart(),
                        event.getWindowEnd(),
                        event.getId())
                // Only policies with a paid premium are covered.
                .filter(policy->Boolean.TRUE.equals(policy.getPremiumPaid()))
                .collectList()
                .<DemoObservationResponse>flatMap(eligiblePolicies->{
                    if(request.isCreateAutomaticClaims()&&!eligiblePolicies.isEmpty()){
                        return createAutomaticClaims(event,eligiblePolicies)
                            .flatMap(claimsCreated->buildResponse(
                                        observation,
                                        evaluationResponse,
                                        event,
                                        "VERIFIED",
                                        "MATCHED",
                                        claimsCreated,
                                        eligiblePolicies.size()+" eligible policies found, "+claimsCreated+" automatic claim(s) created"));
                    }
                        return buildResponse(
                            observation,
                            evaluationResponse,
                            event,
                            "VERIFIED",
                            "MATCHED",
                            0,
                            eligiblePolicies.size()+" eligible policies found, no automatic claims created (createAutomaticClaims=false)");
                });
    }

    /** Operator of the definition that fired, so claims can explain the trigger exactly. */
    private Mono<String> comparisonOperatorFor(TriggerEvent event){
        String fallback="GREATER_THAN_OR_EQUAL";
        if(event.getTriggerDefinitionId()==null){
            return Mono.just(fallback);
        }
        return triggerDefinitionRepository.findById(event.getTriggerDefinitionId())
                .map(definition->definition.getComparisonOperator()!=null
                        ?definition.getComparisonOperator().name()
                        :fallback)
                .defaultIfEmpty(fallback)
                .onErrorReturn(fallback);
    }

    private Mono<Integer> createAutomaticClaims(
            TriggerEvent event,
            List<EligiblePolicyResponse> eligiblePolicies
    ){
        ClaimCreationRequest claimRequest=ClaimCreationRequest.builder()
                .triggerEventId(event.getId())
                .triggerEventReference(event.getEventReference())
                .triggerDefinitionId(event.getTriggerDefinitionId())
                .productId(event.getProductId())
                .locationZoneId(event.getLocationZoneId())
                .perilId(event.getPerilId())
                .measurementType(event.getMeasurementType().name())
                .measurementUnit(event.getMeasurementUnit().name())
                .aggregatedValue(event.getAggregatedValue())
                .thresholdValue(event.getThresholdValue())
                .eventWindowStart(event.getWindowStart())
                .eventWindowEnd(event.getWindowEnd())
                .eligiblePolicies(eligiblePolicies.stream()
                        .map(p->ClaimCreationRequest.EligiblePolicyInfo.builder()
                                .policyId(p.getPolicyId())
                                .policyNumber(p.getPolicyNumber())
                                .customerId(p.getCustomerId())
                                .sumInsured(p.getSumInsured())
                                .build())
                        .toList())
                .build();

        return comparisonOperatorFor(event)
                .flatMap(operator->{
                    claimRequest.setComparisonOperator(operator);
                    return claimsServiceClient.createClaimsFromTriggerEvent(claimRequest);
                })
                .flatMap(response->{
                    log.info("Claims created from trigger event {}: {} created, {} failed",
                            event.getId(),
                            response.getClaimsCreated(),
                            response.getClaimsFailed());

                    for(ClaimCreationResponse.ClaimResult result:response.getResults()){
                        if("CREATED".equals(result.getStatus())||"EXISTING".equals(result.getStatus())){
                            Long customerId=eligiblePolicies.stream()
                                .filter(policy->policy.getPolicyId().equals(result.getPolicyId()))
                                .map(EligiblePolicyResponse::getCustomerId)
                                .findFirst()
                                .orElse(null);
                            notificationServiceClient.createInternalNotification(
                                    InternalNotificationRequest.builder()
                                    .recipientCustomerId(customerId)
                                            .type("AUTOMATIC_CLAIM_CREATED")
                                            .title("Automatic parametric claim created")
                                            .message("Trigger event "+event.getEventReference()+" met the threshold. Claim "+result.getClaimNumber()+" was created automatically for policy "+result.getPolicyNumber()+".")
                                            .relatedEntityType("CLAIM")
                                            .relatedEntityId(result.getClaimId())
                                            .correlationId(event.getEventReference())
                                            .idempotencyKey("AUTO_CLAIM:"+result.getClaimId()+":CUSTOMER:"+customerId)
                                            .deliveryChannel("IN_APP")
                                            .build()
                            ).subscribe();
                        }
                    }
                    return Mono.just(response.getClaimsCreated());
                })
                .onErrorResume(error->{
                    log.error("Failed to create automatic claims for event {}: {}",
                            event.getId(),error.getMessage());
                    return Mono.just(0);
                });
    }

    private Mono<DemoObservationResponse> buildResponse(
            Observation observation,
            org.example.triggerservice.dto.TriggerEvaluationResponse evaluationResponse,
            TriggerEvent event,
            String eventStatus,
            String evaluationResult,
            int claimsCreated,
            String message
    ){
        return Mono.just(DemoObservationResponse.builder()
                .observationId(observation.getId())
                .observationReference(observation.getObservationReference())
                .observationStatus(observation.getStatus().name())
                .evaluationId(evaluationResponse!=null?evaluationResponse.getId():null)
                .evaluationReference(evaluationResponse!=null?evaluationResponse.getEvaluationReference():null)
                .evaluationResult(evaluationResult)
                .aggregatedValue(evaluationResponse!=null?evaluationResponse.getAggregatedValue():null)
                .thresholdValue(evaluationResponse!=null?evaluationResponse.getThresholdValue():null)
                .comparisonOperator(evaluationResponse!=null?evaluationResponse.getComparisonOperator()!=null?evaluationResponse.getComparisonOperator().name():null:null)
                .observationCount(evaluationResponse!=null?evaluationResponse.getObservationCount():null)
                .triggerEventId(event!=null?event.getId():null)
                .triggerEventReference(event!=null?event.getEventReference():null)
                .triggerEventStatus(eventStatus)
                .eligiblePolicyNumbers(List.of())
                .claimsCreated(claimsCreated)
                .message(message)
                .build());
    }

    private String generateReference(){
        return "OBS-"+LocalDateTime.now().getYear()+"-"+
                UUID.randomUUID()
                        .toString()
                        .replace("-","")
                        .substring(0,10)
                        .toUpperCase(Locale.ROOT);
    }

    private String generateEventReference(){
        return "TEV-"+LocalDateTime.now().getYear()+"-"+
                UUID.randomUUID()
                        .toString()
                        .replace("-","")
                        .substring(0,10)
                        .toUpperCase(Locale.ROOT);
    }
}