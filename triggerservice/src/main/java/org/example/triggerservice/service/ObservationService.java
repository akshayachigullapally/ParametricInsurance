package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.CreateObservationRequest;
import org.example.triggerservice.dto.ObservationResponse;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationSource;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.example.triggerservice.util.MeasurementValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObservationService{
    private final ObservationRepository observationRepository;
    private final LocationZoneRepository locationZoneRepository;

    public Flux<ObservationResponse> getAll(){
        return observationRepository.findAllByOrderByObservedAtDesc()
                .flatMap(this::buildResponse);
    }

    public Mono<ObservationResponse> getById(Long observationId){
        validateId(observationId,"Observation ID");

        return observationRepository.findById(observationId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Observation not found")))
                .flatMap(this::buildResponse);
    }

    public Flux<ObservationResponse> getByZoneId(Long zoneId){
        validateId(zoneId,"Location zone ID");

        return findAvailableZone(zoneId)
                .thenMany(observationRepository
                        .findAllByLocationZoneIdOrderByObservedAtDesc(zoneId))
                .flatMap(this::buildResponse);
    }

    public Flux<ObservationResponse> getByMeasurementType(String value){
        MeasurementType measurementType=parseEnum(
                value,
                MeasurementType.class,
                "measurement type"
        );

        return observationRepository
                .findAllByMeasurementTypeOrderByObservedAtDesc(measurementType)
                .flatMap(this::buildResponse);
    }

    public Mono<ObservationResponse> create(
            CreateObservationRequest request,
            Long userId
    ){
        normalize(request);

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

        ObservationSource source=parseEnum(
                request.getSource(),
                ObservationSource.class,
                "observation source"
        );

        MeasurementValidator.validateMeasurementUnit(measurementType,measurementUnit);
        validateObservedValue(measurementType,request.getObservedValue());
        validateObservedAt(request.getObservedAt());
        validateSourceReference(source,request.getSourceReference());

        return findActiveZone(request.getLocationZoneId())
                .then(validateDuplicateSourceReference(
                        request.getSourceReference()))
                .then(Mono.defer(()->{
                    LocalDateTime now=LocalDateTime.now();

                    Observation observation=Observation.builder()
                            .observationReference(generateReference())
                            .locationZoneId(request.getLocationZoneId())
                            .measurementType(measurementType)
                            .measurementUnit(measurementUnit)
                            .observedValue(request.getObservedValue())
                            .observedAt(request.getObservedAt())
                            .source(source)
                            .sourceReference(request.getSourceReference())
                            .status(ObservationStatus.RECEIVED)
                            .receivedBy(userId)
                            .receivedAt(now)
                            .build();

                    log.debug("Recording {} observation {} {} for zone {}",
                            source,request.getObservedValue(),measurementUnit,
                            request.getLocationZoneId());

                    return observationRepository.save(observation);
                }))
                .flatMap(this::buildResponse);
    }

    private Mono<Void> validateDuplicateSourceReference(
            String sourceReference
    ){
        if(!StringUtils.hasText(sourceReference)){
            return Mono.empty();
        }

        return observationRepository.existsBySourceReference(sourceReference)
                .flatMap(exists->{
                    if(exists){
                        return Mono.error(new ConflictException(
                                "An observation with this source reference already exists"
                        ));
                    }

                    return Mono.empty();
                });
    }

    private Mono<LocationZone> findAvailableZone(Long zoneId){
        validateId(zoneId,"Location zone ID");

        return locationZoneRepository.findById(zoneId)
                .filter(zone->
                        zone.getStatus()!=LocationZoneStatus.ARCHIVED)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Location zone not found")));
    }

    private Mono<LocationZone> findActiveZone(Long zoneId){
        return findAvailableZone(zoneId)
                .flatMap(zone->{
                    if(zone.getStatus()!=LocationZoneStatus.ACTIVE){
                        return Mono.error(new ConflictException(
                                "Location zone must be ACTIVE"));
                    }

                    return Mono.just(zone);
                });
    }

    private Mono<ObservationResponse> buildResponse(
            Observation observation
    ){
        return locationZoneRepository
                .findById(observation.getLocationZoneId())
                .defaultIfEmpty(LocationZone.builder()
                        .id(observation.getLocationZoneId())
                        .zoneCode("UNAVAILABLE")
                        .zoneName("Unavailable")
                        .build())
                .map(zone->toResponse(observation,zone));
    }

    private ObservationResponse toResponse(
            Observation observation,
            LocationZone zone
    ){
        return ObservationResponse.builder()
                .id(observation.getId())
                .observationReference(
                        observation.getObservationReference())
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
                .build();
    }

    private void validateObservedValue(
            MeasurementType measurementType,
            BigDecimal value
    ){
        if(value==null){
            throw new ValidationException("Observed value is required");
        }

        switch(measurementType){
            case RAINFALL,WIND_SPEED,RIVER_LEVEL->{
                if(value.compareTo(BigDecimal.ZERO)<0){
                    throw new ValidationException(
                            measurementType.name()+
                                    " value cannot be negative");
                }
            }
            case HUMIDITY,SOIL_MOISTURE->{
                if(value.compareTo(BigDecimal.ZERO)<0||
                        value.compareTo(new BigDecimal("100"))>0){
                    throw new ValidationException(
                            measurementType.name()+
                                    " value must be between 0 and 100");
                }
            }
            case TEMPERATURE->{
                if(value.compareTo(new BigDecimal("-100"))<0||
                        value.compareTo(new BigDecimal("100"))>0){
                    throw new ValidationException(
                            "Temperature must be between -100 and 100 Celsius");
                }
            }
        }
    }

    private void validateObservedAt(LocalDateTime observedAt){
        if(observedAt==null){
            throw new ValidationException(
                    "Observation time is required");
        }

        if(observedAt.isAfter(LocalDateTime.now())){
            throw new ValidationException(
                    "Observation time cannot be in the future");
        }
    }

    private void validateSourceReference(
            ObservationSource source,
            String sourceReference
    ){
        if((source==ObservationSource.WEATHER_API||
                source==ObservationSource.SENSOR)&&
                !StringUtils.hasText(sourceReference)){
            throw new ValidationException(
                    "Source reference is required for "+
                            source.name()+" observations");
        }
    }

    private String generateReference(){
        return "OBS-"+LocalDateTime.now().getYear()+"-"+
                UUID.randomUUID()
                        .toString()
                        .replace("-","")
                        .substring(0,10)
                        .toUpperCase(Locale.ROOT);
    }

    private <T extends Enum<T>> T parseEnum(
            String value,
            Class<T> enumClass,
            String fieldName
    ){
        if(!StringUtils.hasText(value)){
            throw new ValidationException(
                    capitalize(fieldName)+" is required");
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

    private void validateId(Long id,String fieldName){
        if(id==null||id<=0){
            throw new ValidationException(
                    fieldName+" must be greater than zero");
        }
    }

    private void normalize(CreateObservationRequest request){
        request.setMeasurementType(
                normalizeEnumText(request.getMeasurementType()));
        request.setMeasurementUnit(
                normalizeEnumText(request.getMeasurementUnit()));
        request.setSource(normalizeEnumText(request.getSource()));
        request.setSourceReference(
                normalizeNullableText(request.getSourceReference()));
    }

    private String normalizeEnumText(String value){
        return StringUtils.hasText(value)
                ?value.trim().toUpperCase(Locale.ROOT)
                :value;
    }

    private String normalizeNullableText(String value){
        return StringUtils.hasText(value)?value.trim():null;
    }

    private String capitalize(String value){
        if(!StringUtils.hasText(value)){
            return value;
        }

        return Character.toUpperCase(value.charAt(0))+
                value.substring(1);
    }
}