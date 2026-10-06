package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.DemoObservationRequest;
import org.example.triggerservice.dto.DemoObservationResponse;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.DemoObservationService;
import org.example.triggerservice.util.SecurityUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class DemoObservationHandler{
    private static final Set<String> ADMIN_ROLES=Set.of("ROLE_ADMIN");

    private final DemoObservationService demoObservationService;

    public Mono<ServerResponse> recordAndEvaluate(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .flatMap(userId->request.bodyToMono(DemoObservationRequest.class)
                        .switchIfEmpty(Mono.error(new ValidationException("Request body is required")))
                        .flatMap(body->demoObservationService.recordAndEvaluate(body,userId)))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    private Mono<Long> requireUserId(){
        return SecurityUtil.getUserId()
                .switchIfEmpty(Mono.error(new ForbiddenException(
                        "Authenticated user ID is unavailable"
                )));
    }

    private Mono<Void> requireAdmin(){
        return SecurityUtil.getRole()
                .filter(ADMIN_ROLES::contains)
                .switchIfEmpty(Mono.error(new ForbiddenException(
                        "You are not authorized to perform this operation"
                )))
                .then();
    }
}