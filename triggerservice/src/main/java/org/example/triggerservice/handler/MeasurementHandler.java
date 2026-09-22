package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.dto.CurrentMeasurementRequest;
import org.example.triggerservice.service.MeasurementService;
import org.example.triggerservice.util.SecurityUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class MeasurementHandler{
    private static final Set<String> VIEW_ROLES=Set.of(
            "ROLE_ADMIN",
            "ROLE_UNDERWRITER",
            "ROLE_CLAIMS_OFFICER"
    );

    private final MeasurementService measurementService;

    public Mono<ServerResponse> processCurrentMeasurement(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .zipWith(request.bodyToMono(CurrentMeasurementRequest.class)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Request body is required"))))
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
        return requireAnyRole(VIEW_ROLES)
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