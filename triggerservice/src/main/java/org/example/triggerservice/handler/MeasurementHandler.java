package org.example.triggerservice.handler;

import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.dto.CurrentMeasurementRequest;
import org.example.triggerservice.dto.ManualReadingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.example.triggerservice.service.MeasurementService;
import org.example.triggerservice.util.RequestValidationUtil;
import org.example.triggerservice.util.SecurityUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
public class MeasurementHandler{
    private static final Set<String> SNAPSHOT_VIEW_ROLES=Set.of(
            "ROLE_ADMIN",
            "ROLE_UNDERWRITER",
            "ROLE_CLAIMS_OFFICER",
            "ROLE_CUSTOMER"
    );

    private static final Set<String> VIEW_ROLES=Set.of(
            "ROLE_ADMIN",
            "ROLE_UNDERWRITER",
            "ROLE_CLAIMS_OFFICER"
    );

    private final MeasurementService measurementService;
    private final RequestValidationUtil validationUtil;
    private final int manualHoldMinutes;

    public MeasurementHandler(
            MeasurementService measurementService,
            RequestValidationUtil validationUtil,
            @Value("${demo.feed.manual-hold-minutes:60}") int manualHoldMinutes
    ){
        this.measurementService=measurementService;
        this.validationUtil=validationUtil;
        this.manualHoldMinutes=manualHoldMinutes;
    }

    /** Admin sets the live reading of a measurement type (all active zones). */
    public Mono<ServerResponse> recordManualReading(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .zipWith(request.bodyToMono(ManualReadingRequest.class)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Request body is required")))
                        .flatMap(validationUtil::validate))
                .flatMap(tuple->measurementService.recordManualReading(
                        tuple.getT2(),tuple.getT1(),manualHoldMinutes))
                .flatMap(result->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    public Mono<ServerResponse> processCurrentMeasurement(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .zipWith(request.bodyToMono(CurrentMeasurementRequest.class)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Request body is required")))
                        .flatMap(validationUtil::validate))
                .flatMap(tuple->measurementService.processCurrentMeasurement(
                        tuple.getT2(),tuple.getT1()))
                .flatMap(result->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    public Mono<ServerResponse> getMeasurementTypes(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(measurementService.getMeasurementTypes())
                .collectList()
                .flatMap(types->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(types));
    }

    public Mono<ServerResponse> getCurrentMeasurements(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(measurementService.getCurrentMeasurements())
                .collectList()
                .flatMap(measurements->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(measurements));
    }

    public Mono<ServerResponse> getCurrentMeasurementSnapshots(ServerRequest request){
        // Read-only weather readings: customers see them on the zone map.
        return requireAnyRole(SNAPSHOT_VIEW_ROLES)
                .thenMany(measurementService.getCurrentMeasurementSnapshots())
                .collectList()
                .flatMap(snapshots->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(snapshots));
    }

    public Mono<ServerResponse> getCurrentMeasurementsInternal(
            ServerRequest request
    ){
        return measurementService.getCurrentMeasurements()
                .collectList()
                .flatMap(measurements->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(measurements));
    }

    private Mono<Void> requireAnyRole(Set<String> roles){
        return SecurityUtil.getRole()
                .filter(roles::contains)
                .switchIfEmpty(Mono.error(new ForbiddenException(
                        "You are not authorized to perform this operation"
                )))
                .then();
    }

        private Mono<Void> requireAdmin(){
                return requireAnyRole(Set.of("ROLE_ADMIN"));
        }
}