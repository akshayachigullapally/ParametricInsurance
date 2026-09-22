package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.exception.ForbiddenException;
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
}