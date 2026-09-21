package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.TriggerEventResponse;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.Peril;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.model.TriggerEvent;
import org.example.triggerservice.model.TriggerEventStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.PerilRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.example.triggerservice.repository.TriggerEventRepository;
import org.example.triggerservice.service.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TriggerEventService{
    private final TriggerEventRepository triggerEventRepository;
    private final TriggerDefinitionRepository triggerDefinitionRepository;
    private final PerilRepository perilRepository;
    private final LocationZoneRepository locationZoneRepository;
    private final IdempotencyService idempotencyService;
    private final NotificationService notificationService;

    public Flux<TriggerEventResponse> getAll(){
        return triggerEventRepository.findAllByOrderByDetectedAtDesc()
                .flatMap(this::buildResponse);
    }

    public Flux<TriggerEventResponse> getByStatus(String statusValue){
        TriggerEventStatus status=parseStatus(statusValue);

        return triggerEventRepository
                .findAllByStatusOrderByDetectedAtDesc(status)
                .flatMap(this::buildResponse);
    }

    public Flux<TriggerEventResponse> getByDefinitionId(Long definitionId){
        validateId(definitionId,"Trigger definition ID");

        return triggerDefinitionRepository.findById(definitionId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Trigger definition not found")))
                .thenMany(triggerEventRepository
                        .findAllByTriggerDefinitionIdOrderByDetectedAtDesc(
                                definitionId))
                .flatMap(this::buildResponse);
    }

    public Flux<TriggerEventResponse> getByProductId(Long productId){
        validateId(productId,"Product ID");

        return triggerEventRepository
                .findAllByProductIdOrderByDetectedAtDesc(productId)
                .flatMap(this::buildResponse);
    }

    public Flux<TriggerEventResponse> getByZoneId(Long zoneId){
        validateId(zoneId,"Location zone ID");

        return locationZoneRepository.findById(zoneId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Location zone not found")))
                .thenMany(triggerEventRepository
                        .findAllByLocationZoneIdOrderByDetectedAtDesc(zoneId))
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerEventResponse> getById(Long eventId){
        return findEvent(eventId).flatMap(this::buildResponse);
    }

    public Mono<TriggerEventResponse> startValidation(
            Long eventId,
            Long userId
    ){
        return findEvent(eventId)
                .flatMap(event->{
                    if(event.getStatus()!=TriggerEventStatus.DETECTED){
                        return Mono.error(new ConflictException(
                                "Only DETECTED trigger events can start validation"));
                    }

                    event.setStatus(TriggerEventStatus.UNDER_VALIDATION);
                    event.setReviewedBy(userId);
                    event.setReviewedAt(LocalDateTime.now());
                    event.setReviewNotes(null);

                    return triggerEventRepository.save(event);
                })
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerEventResponse> verify(
            Long eventId,
            String reviewNotes,
            Long userId
    ){
        validateNotes(reviewNotes);

        return findEvent(eventId)
                .flatMap(event->{
                    if(event.getStatus()!=
                            TriggerEventStatus.UNDER_VALIDATION){
                        return Mono.error(new ConflictException(
                                "Only UNDER_VALIDATION trigger events can be verified"));
                    }

                    event.setStatus(TriggerEventStatus.VERIFIED);
                    event.setReviewedBy(userId);
                    event.setReviewedAt(LocalDateTime.now());
                    event.setReviewNotes(reviewNotes.trim());

                    return triggerEventRepository.save(event);
                })
                .flatMap(saved -> notificationService.notifyTriggerVerified(
                        null, saved.getId(), saved.getEventReference()
                ).thenReturn(saved))
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerEventResponse> reject(
            Long eventId,
            String reviewNotes,
            Long userId
    ){
        validateNotes(reviewNotes);

        return findEvent(eventId)
                .flatMap(event->{
                    if(event.getStatus()!=
                            TriggerEventStatus.UNDER_VALIDATION){
                        return Mono.error(new ConflictException(
                                "Only UNDER_VALIDATION trigger events can be rejected"));
                    }

                    event.setStatus(TriggerEventStatus.REJECTED);
                    event.setReviewedBy(userId);
                    event.setReviewedAt(LocalDateTime.now());
                    event.setReviewNotes(reviewNotes.trim());

                    return triggerEventRepository.save(event);
                })
                .flatMap(saved -> notificationService.notifyTriggerRejected(
                        null, saved.getId(), saved.getEventReference()
                ).thenReturn(saved))
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerEventResponse> processEvent(
            Long eventId,
            String reviewNotes,
            Long userId
    ){
        validateNotes(reviewNotes);

        return findEvent(eventId)
                .flatMap(event->{
                    if(event.getStatus()!=TriggerEventStatus.VERIFIED){
                        return Mono.error(new ConflictException(
                                "Only VERIFIED trigger events can be processed"));
                    }

                    String idempotencyKey=IdempotencyService.buildTriggerEventKey(
                            event.getTriggerDefinitionId(),
                            event.getWindowStart(),
                            event.getWindowEnd());

                    return idempotencyService.isDuplicate(idempotencyKey)
                            .flatMap(isDuplicate->{
                                if(isDuplicate){
                                    return Mono.error(new ConflictException(
                                            "This trigger event has already been processed"));
                                }

                                event.setStatus(TriggerEventStatus.PROCESSED);
                                event.setReviewedBy(userId);
                                event.setReviewedAt(LocalDateTime.now());
                                event.setReviewNotes(reviewNotes.trim());

                                return triggerEventRepository.save(event)
                                        .flatMap(savedEvent->
                                                idempotencyService.record(
                                                        idempotencyKey,
                                                        "PROCESSED_EVENT",
                                                        savedEvent.getId())
                                                        .map(r->savedEvent));
                            });
                })
                .flatMap(this::buildResponse);
    }

    public Mono<Void> markEventProcessed(Long eventId){
        validateId(eventId,"Trigger event ID");

        return triggerEventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Trigger event not found")))
                .flatMap(event->{
                    if(event.getStatus()!=TriggerEventStatus.VERIFIED){
                        return Mono.error(new ConflictException(
                                "Only VERIFIED trigger events can be marked as processed"));
                    }

                    event.setStatus(TriggerEventStatus.PROCESSED);
                    event.setReviewNotes("Marked processed by financial service");

                    return triggerEventRepository.save(event).then();
                });
    }

    private Mono<TriggerEvent> findEvent(Long eventId){
        validateId(eventId,"Trigger event ID");

        return triggerEventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Trigger event not found")));
    }

    private Mono<TriggerEventResponse> buildResponse(TriggerEvent event){
        Mono<TriggerDefinition> definitionMono=
                triggerDefinitionRepository
                        .findById(event.getTriggerDefinitionId())
                        .defaultIfEmpty(TriggerDefinition.builder()
                                .id(event.getTriggerDefinitionId())
                                .definitionCode("UNAVAILABLE")
                                .definitionName("Unavailable")
                                .build());

        Mono<Peril> perilMono=perilRepository.findById(event.getPerilId())
                .defaultIfEmpty(Peril.builder()
                        .id(event.getPerilId())
                        .perilCode("UNAVAILABLE")
                        .perilName("Unavailable")
                        .build());

        Mono<LocationZone> zoneMono=locationZoneRepository
                .findById(event.getLocationZoneId())
                .defaultIfEmpty(LocationZone.builder()
                        .id(event.getLocationZoneId())
                        .zoneCode("UNAVAILABLE")
                        .zoneName("Unavailable")
                        .build());

        return Mono.zip(definitionMono,perilMono,zoneMono)
                .map(tuple->TriggerEventResponse.builder()
                        .id(event.getId())
                        .eventReference(event.getEventReference())
                        .triggerDefinitionId(
                                event.getTriggerDefinitionId())
                        .definitionCode(
                                tuple.getT1().getDefinitionCode())
                        .definitionName(
                                tuple.getT1().getDefinitionName())
                        .triggerEvaluationId(
                                event.getTriggerEvaluationId())
                        .perilId(event.getPerilId())
                        .perilCode(tuple.getT2().getPerilCode())
                        .perilName(tuple.getT2().getPerilName())
                        .locationZoneId(event.getLocationZoneId())
                        .zoneCode(tuple.getT3().getZoneCode())
                        .zoneName(tuple.getT3().getZoneName())
                        .productId(event.getProductId())
                        .measurementType(event.getMeasurementType())
                        .measurementUnit(event.getMeasurementUnit())
                        .aggregatedValue(event.getAggregatedValue())
                        .thresholdValue(event.getThresholdValue())
                        .windowStart(event.getWindowStart())
                        .windowEnd(event.getWindowEnd())
                        .status(event.getStatus())
                        .detectedAt(event.getDetectedAt())
                        .reviewedBy(event.getReviewedBy())
                        .reviewedAt(event.getReviewedAt())
                        .reviewNotes(event.getReviewNotes())
                        .build());
    }

    private TriggerEventStatus parseStatus(String value){
        if(!StringUtils.hasText(value)){
            throw new ValidationException(
                    "Trigger event status is required");
        }

        try{
            return TriggerEventStatus.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
        }catch(IllegalArgumentException exception){
            throw new ValidationException(
                    "Invalid trigger event status");
        }
    }

    private void validateNotes(String reviewNotes){
        if(!StringUtils.hasText(reviewNotes)){
            throw new ValidationException("Review notes are required");
        }

        if(reviewNotes.trim().length()>500){
            throw new ValidationException(
                    "Review notes cannot exceed 500 characters");
        }
    }

    private void validateId(Long id,String fieldName){
        if(id==null||id<=0){
            throw new ValidationException(
                    fieldName+" must be greater than zero");
        }
    }
}