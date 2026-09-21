package org.example.notificationservice.handler;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.UpsertNotificationPreferenceRequest;
import org.example.notificationservice.service.NotificationPreferenceService;
import org.example.notificationservice.util.RequestValidationUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class NotificationPreferenceHandler {

    private final NotificationPreferenceService service;
    private final RequestValidationUtil validation;

    public NotificationPreferenceHandler(NotificationPreferenceService service, RequestValidationUtil validation) {
        this.service = service;
        this.validation = validation;
    }

    public Mono<ServerResponse> upsert(ServerRequest r) {
        return r.bodyToMono(UpsertNotificationPreferenceRequest.class)
                .map(validation::validate)
                .flatMap(service::upsert)
                .flatMap(x -> ServerResponse.status(HttpStatus.OK).bodyValue(x));
    }

    public Mono<ServerResponse> myPreferences(ServerRequest r) {
        return ServerResponse.ok().body(service.myPreferences(), Object.class);
    }
}
