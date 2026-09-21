package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.dto.LocationZoneRequest;
import org.example.triggerservice.dto.MessageResponse;
import org.example.triggerservice.dto.UpdateStatusRequest;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.LocationZoneService;
import org.example.triggerservice.util.RequestValidationUtil;
import org.example.triggerservice.util.SecurityUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationZoneHandler{
    private static final Set<String> VIEW_ROLES=Set.of(
            "ROLE_ADMIN","ROLE_UNDERWRITER","ROLE_CLAIMS_OFFICER"
    );

    private final LocationZoneService locationZoneService;
    private final RequestValidationUtil validationUtil;

    public Mono<ServerResponse> getAll(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(locationZoneService.getAll(request.queryParam("state").orElse(null)))
                .collectList()
                .flatMap(zones->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(zones));
    }

    public Mono<ServerResponse> getById(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"zoneId"))
                .flatMap(locationZoneService::getById)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getInternalById(ServerRequest request){
        try{
            Long zoneId=Long.parseLong(request.pathVariable("zoneId"));
            return locationZoneService.getInternalById(zoneId)
                    .flatMap(response->ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(response));
        }catch(NumberFormatException exception){
            return Mono.error(new ValidationException("Invalid zoneId"));
        }
    }

    public Mono<ServerResponse> create(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .switchIfEmpty(Mono.error(new ForbiddenException("Authenticated user ID is unavailable")))
                .flatMap(userId->request.bodyToMono(LocationZoneRequest.class)
                        .switchIfEmpty(Mono.error(new ValidationException("Request body is required")))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->locationZoneService.create(body,userId)))
                .flatMap(response->ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> update(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .switchIfEmpty(Mono.error(new ForbiddenException("Authenticated user ID is unavailable")))
                .zipWith(parseId(request,"zoneId"))
                .flatMap(tuple->request.bodyToMono(LocationZoneRequest.class)
                        .switchIfEmpty(Mono.error(new ValidationException("Request body is required")))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->locationZoneService.update(tuple.getT2(),body,tuple.getT1())))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> updateStatus(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .switchIfEmpty(Mono.error(new ForbiddenException("Authenticated user ID is unavailable")))
                .zipWith(parseId(request,"zoneId"))
                .flatMap(tuple->request.bodyToMono(UpdateStatusRequest.class)
                        .switchIfEmpty(Mono.error(new ValidationException("Request body is required")))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->locationZoneService.updateStatus(
                                tuple.getT2(),body.getStatus(),tuple.getT1())))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> archive(ServerRequest request){
        return requireAdmin()
                .then(SecurityUtil.getUserId())
                .switchIfEmpty(Mono.error(new ForbiddenException("Authenticated user ID is unavailable")))
                .zipWith(parseId(request,"zoneId"))
                .flatMap(tuple->locationZoneService.archive(tuple.getT2(),tuple.getT1()))
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(MessageResponse.builder()
                                .message("Location zone archived successfully")
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

    public Mono<ServerResponse> getActive(ServerRequest request){
        log.info("GET /api/triggers/zones/active called");
        return requireAnyRole(Set.of(
                "ROLE_CUSTOMER",
                "ROLE_ADMIN",
                "ROLE_UNDERWRITER",
                "ROLE_CLAIMS_OFFICER"
        ))
                .thenMany(locationZoneService.getActive())
                .collectList()
                .doOnNext(zones -> log.info("Returning {} active zones", zones.size()))
                .flatMap(zones->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(zones));
    }

    public Mono<ServerResponse> getInternalActive(ServerRequest request){
        return locationZoneService.getActiveInternal()
                .collectList()
                .flatMap(zones->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(zones));
    }
}