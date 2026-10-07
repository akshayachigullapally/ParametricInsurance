package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.ZoneAlertRequest;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.client.ProductTriggerClient;
import org.example.triggerservice.dto.InternalZoneResponse;
import org.example.triggerservice.dto.LocationZoneRequest;
import org.example.triggerservice.dto.LocationZoneResponse;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationZoneService{
    private final LocationZoneRepository locationZoneRepository;
    private final ProductTriggerClient productTriggerClient;
    private final NotificationService notificationService;

    public Flux<LocationZoneResponse> getAll(String state){
        log.debug("Get all [location zone]: state={}",state);
        if(StringUtils.hasText(state)){
            return locationZoneRepository
                    .findAllByStateIgnoreCaseAndStatusOrderByZoneNameAsc(
                            state.trim(),
                            LocationZoneStatus.ACTIVE
                    )
                    .map(this::toResponse);
        }

        return locationZoneRepository
                .findAllByStatusNotOrderByZoneNameAsc(LocationZoneStatus.ARCHIVED)
                .map(this::toResponse);
    }

    public Flux<LocationZoneResponse> getActive(){
        return locationZoneRepository
                .findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE)
                .doOnSubscribe(subscription->
                        log.info("Fetching active location zones for public API"))
                .doOnNext(zone->
                        log.info(
                                "Active public zone found: zoneId={}, zoneCode={}, zoneName={}, status={}",
                                zone.getId(),
                                zone.getZoneCode(),
                                zone.getZoneName(),
                                zone.getStatus()
                        ))
                .doOnComplete(()->
                        log.info("Completed fetching active location zones for public API"))
                .doOnError(error->
                        log.error(
                                "Failed to fetch active location zones for public API: {}",
                                error.getMessage(),
                                error
                        ))
                .map(this::toResponse);
    }

    public Mono<LocationZoneResponse> getById(Long zoneId){
        log.debug("Get by id [location zone]: zoneId={}",zoneId);
        return findAvailableZone(zoneId).map(this::toResponse);
    }

    public Mono<InternalZoneResponse> getInternalById(Long zoneId){
        log.debug("Get internal by id [location zone]: zoneId={}",zoneId);
        return locationZoneRepository.findById(zoneId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Location zone not found")
                ))
                .map(this::toInternalResponse);
    }

    public Flux<InternalZoneResponse> getActiveInternal(){
        return locationZoneRepository
                .findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE)
                .doOnSubscribe(subscription->
                        log.info("Received internal request to fetch active location zones"))
                .doOnNext(zone->
                        log.info(
                                "Active internal zone found: zoneId={}, zoneCode={}, zoneName={}, state={}, status={}",
                                zone.getId(),
                                zone.getZoneCode(),
                                zone.getZoneName(),
                                zone.getState(),
                                zone.getStatus()
                        ))
                .doOnComplete(()->
                        log.info("Completed internal active location zones request"))
                .doOnCancel(()->
                        log.warn("Internal active location zones request was cancelled"))
                .doOnError(error->
                        log.error(
                                "Failed to fetch active location zones for internal request: {}",
                                error.getMessage(),
                                error
                        ))
                .map(this::toInternalResponse);
    }

    public Mono<LocationZoneResponse> create(LocationZoneRequest request,Long userId){
        log.info("Create [location zone]: userId={}",userId);
        normalize(request);

        return locationZoneRepository.existsByZoneCodeIgnoreCase(request.getZoneCode())
                .flatMap(exists->{
                    if(exists){
                        return Mono.error(
                                new ConflictException("Location zone code already exists")
                        );
                    }

                    LocalDateTime now=LocalDateTime.now();

                    LocationZone zone=LocationZone.builder()
                            .zoneCode(request.getZoneCode())
                            .zoneName(request.getZoneName())
                            .country(request.getCountry())
                            .state(request.getState())
                            .district(request.getDistrict())
                            .postalCode(request.getPostalCode())
                            .latitude(request.getLatitude())
                            .longitude(request.getLongitude())
                            .radiusKm(request.getRadiusKm())
                            .status(LocationZoneStatus.ACTIVE)
                            .createdBy(userId)
                            .createdAt(now)
                            .updatedBy(userId)
                            .updatedAt(now)
                            .build();

                    return locationZoneRepository.save(zone);
                })
                .map(this::toResponse);
    }

    public Mono<LocationZoneResponse> update(
            Long zoneId,
            LocationZoneRequest request,
            Long userId
    ){
        log.info("Update [location zone]: zoneId={}, userId={}",zoneId,userId);
        normalize(request);

        return findAvailableZone(zoneId)
                .flatMap(zone->
                        locationZoneRepository.existsByZoneCodeAndIdNot(
                                        request.getZoneCode(),
                                        zoneId
                                )
                                .flatMap(exists->{
                                    if(exists){
                                        return Mono.error(
                                                new ConflictException(
                                                        "Location zone code already exists"
                                                )
                                        );
                                    }


                                    zone.setZoneCode(request.getZoneCode());
                                    zone.setZoneName(request.getZoneName());
                                    zone.setCountry(request.getCountry());
                                    zone.setState(request.getState());
                                    zone.setDistrict(request.getDistrict());
                                    zone.setPostalCode(request.getPostalCode());
                                    zone.setLatitude(request.getLatitude());
                                    zone.setLongitude(request.getLongitude());
                                    zone.setRadiusKm(request.getRadiusKm());
                                    zone.setUpdatedBy(userId);
                                    zone.setUpdatedAt(LocalDateTime.now());

                                    return locationZoneRepository.save(zone);
                                })
                )
                .map(this::toResponse);
    }

    public Mono<LocationZoneResponse> updateStatus(
            Long zoneId,
            String statusValue,
            Long userId
    ){
        log.info("Update status [location zone]: zoneId={}, statusValue={}, userId={}",zoneId,statusValue,userId);
        LocationZoneStatus status=parseStatus(statusValue);

        if(status==LocationZoneStatus.ARCHIVED){
            return Mono.error(
                    new ValidationException(
                            "Use the delete endpoint to archive a location zone"
                    )
            );
        }

        return findAvailableZone(zoneId)
                .flatMap(zone->{
                    if(zone.getStatus()==status){
                        return Mono.error(
                                new ConflictException(
                                        "Location zone is already "+status.name()
                                )
                        );
                    }

                    LocationZoneStatus oldStatus=zone.getStatus();
                    zone.setStatus(status);
                    zone.setUpdatedBy(userId);
                    zone.setUpdatedAt(LocalDateTime.now());

                    return locationZoneRepository.save(zone);
                })
                .map(this::toResponse);
    }

    public Mono<Void> archive(Long zoneId,Long userId){
        log.info("Archive [location zone]: zoneId={}, userId={}",zoneId,userId);
        return findAvailableZone(zoneId)
                .flatMap(zone->
                        productTriggerClient.hasActivePoliciesForZone(zoneId)
                                .flatMap(hasActivePolicies->{
                                    if(Boolean.TRUE.equals(hasActivePolicies)){
                                        return Mono.error(new ConflictException(
                                                "Cannot archive location zone: it has pending or active policies referencing it"
                                        ));
                                    }

                                    zone.setStatus(LocationZoneStatus.ARCHIVED);
                                    zone.setUpdatedBy(userId);
                                    zone.setUpdatedAt(LocalDateTime.now());

                                    return locationZoneRepository.save(zone);
                                })
                )
                .then();
    }

    private Mono<LocationZone> findAvailableZone(Long zoneId){
        if(zoneId==null||zoneId<=0){
            return Mono.error(
                    new ValidationException(
                            "Location zone ID must be greater than zero"
                    )
            );
        }

        return locationZoneRepository.findById(zoneId)
                .filter(zone->zone.getStatus()!=LocationZoneStatus.ARCHIVED)
                .switchIfEmpty(
                        Mono.error(new NotFoundException("Location zone not found"))
                );
    }

    private LocationZoneStatus parseStatus(String statusValue){
        if(!StringUtils.hasText(statusValue)){
            throw new ValidationException("Location zone status is required");
        }

        try{
            return LocationZoneStatus.valueOf(
                    statusValue.trim().toUpperCase(Locale.ROOT)
            );
        }catch(IllegalArgumentException exception){
            throw new ValidationException(
                    "Invalid location zone status. Allowed values are ACTIVE and INACTIVE"
            );
        }
    }

    private void normalize(LocationZoneRequest request){
        if(request==null){
            throw new ValidationException("Location zone request is required");
        }

        request.setZoneCode(normalizeCode(request.getZoneCode()));
        request.setZoneName(normalizeText(request.getZoneName()));
        request.setCountry(normalizeText(request.getCountry()));
        request.setState(normalizeText(request.getState()));
        request.setDistrict(normalizeText(request.getDistrict()));
        request.setPostalCode(normalizeNullableText(request.getPostalCode()));
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

    private String normalizeNullableText(String value){
        return StringUtils.hasText(value)?value.trim():null;
    }

    private LocationZoneResponse toResponse(LocationZone zone){
        return LocationZoneResponse.builder()
                .id(zone.getId())
                .zoneCode(zone.getZoneCode())
                .zoneName(zone.getZoneName())
                .country(zone.getCountry())
                .state(zone.getState())
                .district(zone.getDistrict())
                .postalCode(zone.getPostalCode())
                .latitude(zone.getLatitude())
                .longitude(zone.getLongitude())
                .radiusKm(zone.getRadiusKm())
                .status(zone.getStatus())
                .createdBy(zone.getCreatedBy())
                .createdAt(zone.getCreatedAt())
                .updatedBy(zone.getUpdatedBy())
                .updatedAt(zone.getUpdatedAt())
                .weatherAlert(Boolean.TRUE.equals(zone.getWeatherAlert()))
                .alertMessage(zone.getAlertMessage())
                .alertRaisedAt(zone.getAlertRaisedAt())
                .maxExposure(zone.getMaxExposure())
                .build();
    }

    private InternalZoneResponse toInternalResponse(LocationZone zone){
        return InternalZoneResponse.builder()
                .zoneId(zone.getId())
                .zoneCode(zone.getZoneCode())
                .zoneName(zone.getZoneName())
                .country(zone.getCountry())
                .state(zone.getState())
                .district(zone.getDistrict())
                .postalCode(zone.getPostalCode())
                .latitude(zone.getLatitude())
                .longitude(zone.getLongitude())
                .radiusKm(zone.getRadiusKm())
                .status(zone.getStatus().name())
                .weatherAlert(Boolean.TRUE.equals(zone.getWeatherAlert()))
                .alertMessage(zone.getAlertMessage())
                .alertRaisedAt(zone.getAlertRaisedAt())
                .maxExposure(zone.getMaxExposure())
                .build();
    }

    /** Raises or clears the zone's weather alert; while raised no new policies can be sold there. */
    public Mono<LocationZoneResponse> updateAlert(Long zoneId,ZoneAlertRequest request,Long userId){
        log.info("Update alert [location zone]: zoneId={}, active={}, userId={}",zoneId,request.getActive(),userId);
        boolean raise=Boolean.TRUE.equals(request.getActive());
        String message=StringUtils.hasText(request.getMessage())
                ?request.getMessage().trim()
                :"Weather alert in effect";

        return findAvailableZone(zoneId)
                .flatMap(zone->{
                    boolean wasRaised=Boolean.TRUE.equals(zone.getWeatherAlert());
                    if(!raise&&!wasRaised){
                        return Mono.error(new ConflictException("Location zone has no active weather alert"));
                    }
                    LocalDateTime now=LocalDateTime.now();
                    zone.setWeatherAlert(raise);
                    zone.setAlertMessage(raise?message:null);
                    zone.setAlertRaisedAt(raise?(wasRaised?zone.getAlertRaisedAt():now):null);
                    zone.setUpdatedBy(userId);
                    zone.setUpdatedAt(now);
                    return locationZoneRepository.save(zone);
                })
                .map(this::toResponse);
    }

    /** Sets (or with null removes) the maximum total sum insured the zone may carry. */
    public Mono<LocationZoneResponse> updateExposureCap(Long zoneId,BigDecimal maxExposure,Long userId){
        log.info("Update exposure cap [location zone]: zoneId={}, maxExposure={}, userId={}",zoneId,maxExposure,userId);
        if(maxExposure!=null&&maxExposure.signum()<=0){
            return Mono.error(new ValidationException("Exposure cap must be greater than zero"));
        }

        return findAvailableZone(zoneId)
                .flatMap(zone->{
                    zone.setMaxExposure(maxExposure);
                    zone.setUpdatedBy(userId);
                    zone.setUpdatedAt(LocalDateTime.now());
                    return locationZoneRepository.save(zone);
                })
                .map(this::toResponse);
    }
}
