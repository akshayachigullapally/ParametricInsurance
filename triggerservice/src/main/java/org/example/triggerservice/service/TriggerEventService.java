package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerEventService{
    private final TriggerEventRepository triggerEventRepository;
    private final TriggerDefinitionRepository triggerDefinitionRepository;
    private final PerilRepository perilRepository;
    private final LocationZoneRepository locationZoneRepository;

    public Flux<TriggerEventResponse> getAll(){
        log.debug("Get all [trigger event]");
        return triggerEventRepository.findAllByOrderByDetectedAtDesc()
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerEventResponse> getById(Long eventId){
        log.debug("Get by id [trigger event]: eventId={}",eventId);
        return findEvent(eventId).flatMap(this::buildResponse);
    }

    public Mono<Void> markEventProcessed(Long eventId){
        log.info("Mark event processed [trigger event]: eventId={}",eventId);
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
                // An event can have no definition or peril (e.g. a product without synced definitions).
                Mono.justOrEmpty(event.getTriggerDefinitionId())
                        .flatMap(triggerDefinitionRepository::findById)
                        .defaultIfEmpty(TriggerDefinition.builder()
                                .id(event.getTriggerDefinitionId())
                                .definitionCode("UNAVAILABLE")
                                .definitionName("Unavailable")
                                .build());

        Mono<Peril> perilMono=Mono.justOrEmpty(event.getPerilId())
                .flatMap(perilRepository::findById)
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

    private void validateId(Long id,String fieldName){
        if(id==null||id<=0){
            throw new ValidationException(
                    fieldName+" must be greater than zero");
        }
    }
}