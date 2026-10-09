package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.MessageResponse;
import org.example.triggerservice.dto.PerilRequest;
import org.example.triggerservice.dto.UpdateStatusRequest;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.PerilService;
import org.example.triggerservice.util.RequestValidationUtil;
import org.example.triggerservice.util.SecurityUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PerilHandler{
    private static final Set<String> VIEW_ROLES=Set.of(
            "ROLE_ADMIN","ROLE_UNDERWRITER","ROLE_CLAIMS_OFFICER"
    );

    private final PerilService perilService;
    private final RequestValidationUtil validationUtil;

    public Mono<ServerResponse> getAll(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(perilService.getAll())
                .collectList()
                .flatMap(perils->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(perils));
    }

    public Mono<ServerResponse> getById(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"perilId"))
                .flatMap(perilId->perilService.getById(perilId))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> create(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .switchIfEmpty(Mono.error(new ForbiddenException("Authenticated user ID is unavailable")))
                .flatMap(userId->request.bodyToMono(PerilRequest.class)
                        .switchIfEmpty(Mono.error(new ValidationException("Request body is required")))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->perilService.create(body,userId)))
                .flatMap(response->ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> update(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .switchIfEmpty(Mono.error(new ForbiddenException("Authenticated user ID is unavailable")))
                .zipWith(parseId(request,"perilId"))
                .flatMap(tuple->request.bodyToMono(PerilRequest.class)
                        .switchIfEmpty(Mono.error(new ValidationException("Request body is required")))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->perilService.update(tuple.getT2(),body,tuple.getT1())))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> updateStatus(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .switchIfEmpty(Mono.error(new ForbiddenException("Authenticated user ID is unavailable")))
                .zipWith(parseId(request,"perilId"))
                .flatMap(tuple->request.bodyToMono(UpdateStatusRequest.class)
                        .switchIfEmpty(Mono.error(new ValidationException("Request body is required")))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->perilService.updateStatus(
                                tuple.getT2(),body.getStatus(),tuple.getT1())))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getArchived(ServerRequest request){
        return requireAdmin()
                .thenMany(perilService.getArchived())
                .collectList()
                .flatMap(perils->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(perils));
    }

    public Mono<ServerResponse> restore(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .switchIfEmpty(Mono.error(new ForbiddenException("Authenticated user ID is unavailable")))
                .zipWith(parseId(request,"perilId"))
                .flatMap(tuple->perilService.restore(tuple.getT2(),tuple.getT1()))
                .flatMap(peril->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(peril));
    }

    public Mono<ServerResponse> archive(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .switchIfEmpty(Mono.error(new ForbiddenException("Authenticated user ID is unavailable")))
                .zipWith(parseId(request,"perilId"))
                .flatMap(tuple->perilService.archive(tuple.getT2(),tuple.getT1()))
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(MessageResponse.builder()
                                .message("Peril archived successfully")
                                .build()));
    }

    private Mono<Void> requireAdmin(){
        return requireAnyRole(Set.of("ROLE_ADMIN"));
    }

    private Mono<Void> requireAnyRole(Set<String> roles){
        return SecurityUtil.getRole()
                .filter(roles::contains)
                .switchIfEmpty(Mono.error(new ForbiddenException("You are not authorized to perform this operation")))
                .then();
    }

    private Mono<Long> parseId(ServerRequest request,String variable){
        try{
            return Mono.just(Long.parseLong(request.pathVariable(variable)));
        }catch(NumberFormatException exception){
            return Mono.error(new ValidationException("Invalid "+variable));
        }
    }
}