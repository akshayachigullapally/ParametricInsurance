package org.example.notificationservice.handler;

import org.example.notificationservice.dto.InternalAuditEventRequest;
import org.example.notificationservice.service.AuditEventService;
import org.example.notificationservice.util.RequestValidationUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class AuditEventHandler{
private final AuditEventService service; private final RequestValidationUtil validation;
public AuditEventHandler(AuditEventService service,RequestValidationUtil validation){this.service=service;this.validation=validation;}
public Mono<ServerResponse> create(ServerRequest r){return r.bodyToMono(InternalAuditEventRequest.class).map(validation::validate).flatMap(service::create).flatMap(x->ServerResponse.status(HttpStatus.CREATED).bodyValue(x));}
public Mono<ServerResponse> all(ServerRequest r){return ServerResponse.ok().body(service.all(),Object.class);}
}