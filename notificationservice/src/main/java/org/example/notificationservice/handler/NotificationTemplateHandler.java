package org.example.notificationservice.handler;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.CreateNotificationTemplateRequest;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.service.NotificationTemplateService;
import org.example.notificationservice.util.RequestValidationUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class NotificationTemplateHandler {

    private final NotificationTemplateService service;
    private final RequestValidationUtil validation;

    public NotificationTemplateHandler(NotificationTemplateService service, RequestValidationUtil validation) {
        this.service = service;
        this.validation = validation;
    }

    public Mono<ServerResponse> create(ServerRequest r) {
        return r.bodyToMono(CreateNotificationTemplateRequest.class)
                .map(validation::validate)
                .flatMap(service::create)
                .flatMap(x -> ServerResponse.status(HttpStatus.CREATED).bodyValue(x));
    }

    public Mono<ServerResponse> update(ServerRequest r) {
        Long id = id(r);
        return r.bodyToMono(CreateNotificationTemplateRequest.class)
                .map(validation::validate)
                .flatMap(body -> service.update(id, body))
                .flatMap(x -> ServerResponse.ok().bodyValue(x));
    }

    public Mono<ServerResponse> get(ServerRequest r) {
        return service.get(id(r)).flatMap(x -> ServerResponse.ok().bodyValue(x));
    }

    public Mono<ServerResponse> all(ServerRequest r) {
        return ServerResponse.ok().body(service.all(), Object.class);
    }

    public Mono<ServerResponse> deactivate(ServerRequest r) {
        return service.deactivate(id(r))
                .then(ServerResponse.ok().bodyValue(new org.example.notificationservice.dto.MessageResponse("Template deactivated")));
    }

    public Mono<ServerResponse> activate(ServerRequest r) {
        return service.activate(id(r))
                .then(ServerResponse.ok().bodyValue(new org.example.notificationservice.dto.MessageResponse("Template activated")));
    }

    public Mono<ServerResponse> defaults(ServerRequest r) {
        return ServerResponse.ok().body(service.defaults(), Object.class);
    }

    private Long id(ServerRequest r) {
        try {
            return Long.valueOf(r.pathVariable("templateId"));
        } catch (Exception e) {
            throw new ValidationException("Invalid templateId");
        }
    }
}
