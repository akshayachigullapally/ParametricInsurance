package org.example.notificationservice.handler;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.service.DeliveryAttemptService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DeliveryAttemptHandler {

    private final DeliveryAttemptService service;

    public DeliveryAttemptHandler(DeliveryAttemptService service) {
        this.service = service;
    }

    public Mono<ServerResponse> getByNotification(ServerRequest r) {
        Long notificationId;
        try {
            notificationId = Long.valueOf(r.pathVariable("notificationId"));
        } catch (Exception e) {
            throw new ValidationException("Invalid notificationId");
        }
        return ServerResponse.ok().body(service.getByNotificationId(notificationId), Object.class);
    }
}
