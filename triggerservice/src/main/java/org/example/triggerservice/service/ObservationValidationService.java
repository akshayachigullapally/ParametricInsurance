package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.dto.ObservationResponse;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObservationValidationService{
    private final ObservationRepository observationRepository;
    private final LocationZoneRepository locationZoneRepository;

    public Flux<ObservationResponse> getReceivedObservations(){
        return observationRepository.findAllByOrderByObservedAtDesc()
                .filter(obs->obs.getStatus()==ObservationStatus.RECEIVED)
                .flatMap(this::buildResponse);
    }

    public Mono<ObservationResponse> validateObservation(
            Long observationId,
            Long userId
    ){
        return findReceivedObservation(observationId)
                .flatMap(observation->{
                    validateObservationRanges(observation);

                    observation.setStatus(ObservationStatus.VALIDATED);
                    return observationRepository.save(observation);
                })
                .flatMap(this::buildResponse)
                .doOnNext(response->log.info(
                        "Observation {} validated by user {}",
                        response.getObservationReference(), userId));
    }

    public Mono<ObservationResponse> rejectObservation(
            Long observationId,
            String reason,
            Long userId
    ){
        return findReceivedObservation(observationId)
                .flatMap(observation->{
                    observation.setStatus(ObservationStatus.REJECTED);
                    return observationRepository.save(observation);
                })
                .flatMap(this::buildResponse)
                .doOnNext(response->log.info(
                        "Observation {} rejected by user {}",
                        response.getObservationReference(), userId));
    }

    public Mono<Long> validateAllReceived(Long userId){
        return observationRepository.findAllByOrderByObservedAtDesc()
                .filter(obs->obs.getStatus()==ObservationStatus.RECEIVED)
                .collectList()
                .flatMapMany(observations->{
                    if(observations.isEmpty()){
                        return Flux.empty();
                    }

                    return Flux.fromIterable(observations)
                            .filter(this::passesRangeValidation)
                            .flatMap(observation->{
                                observation.setStatus(ObservationStatus.VALIDATED);
                                return observationRepository.save(observation);
                            });
                })
                .count()
                .doOnNext(count->log.info(
                        "Bulk validated {} observations by user {}",
                        count, userId));
    }

    private void validateObservationRanges(Observation observation){
        switch(observation.getMeasurementType()){
            case RAINFALL,WIND_SPEED,RIVER_LEVEL->{
                if(observation.getObservedValue().compareTo(BigDecimal.ZERO)<0){
                    throw new ValidationException(
                            observation.getMeasurementType().name()+
                                    " value cannot be negative");
                }
            }
            case HUMIDITY,SOIL_MOISTURE->{
                BigDecimal value=observation.getObservedValue();
                if(value.compareTo(BigDecimal.ZERO)<0||
                        value.compareTo(new BigDecimal("100"))>0){
                    throw new ValidationException(
                            observation.getMeasurementType().name()+
                                    " value must be between 0 and 100");
                }
            }
            case TEMPERATURE->{
                BigDecimal value=observation.getObservedValue();
                if(value.compareTo(new BigDecimal("-100"))<0||
                        value.compareTo(new BigDecimal("100"))>0){
                    throw new ValidationException(
                            "Temperature must be between -100 and 100 Celsius");
                }
            }
        }
    }

    private boolean passesRangeValidation(Observation observation){
        try{
            validateObservationRanges(observation);
            return true;
        }catch(ValidationException e){
            log.warn("Observation {} failed range validation: {}",
                    observation.getObservationReference(), e.getMessage());
            return false;
        }
    }

    private Mono<Observation> findReceivedObservation(Long observationId){
        if(observationId==null||observationId<=0){
            throw new ValidationException("Observation ID must be greater than zero");
        }

        return observationRepository.findById(observationId)
                .switchIfEmpty(Mono.error(new NotFoundException("Observation not found")))
                .flatMap(observation->{
                    if(observation.getStatus()!=ObservationStatus.RECEIVED){
                        return Mono.error(new ConflictException(
                                "Only RECEIVED observations can be validated or rejected"));
                    }
                    return Mono.just(observation);
                });
    }

    private Mono<ObservationResponse> buildResponse(Observation observation){
        return locationZoneRepository
                .findById(observation.getLocationZoneId())
                .defaultIfEmpty(LocationZone.builder()
                        .id(observation.getLocationZoneId())
                        .zoneCode("UNAVAILABLE")
                        .zoneName("Unavailable")
                        .build())
                .map(zone->ObservationResponse.builder()
                        .id(observation.getId())
                        .observationReference(observation.getObservationReference())
                        .locationZoneId(observation.getLocationZoneId())
                        .zoneCode(zone.getZoneCode())
                        .zoneName(zone.getZoneName())
                        .measurementType(observation.getMeasurementType())
                        .measurementUnit(observation.getMeasurementUnit())
                        .observedValue(observation.getObservedValue())
                        .observedAt(observation.getObservedAt())
                        .source(observation.getSource())
                        .sourceReference(observation.getSourceReference())
                        .status(observation.getStatus())
                        .receivedBy(observation.getReceivedBy())
                        .receivedAt(observation.getReceivedAt())
                        .build());
    }
}
