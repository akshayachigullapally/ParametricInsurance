package org.example.notificationservice.handler;

import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Component
public class NotificationSseHandler {

    private final NotificationService service;

    public NotificationSseHandler(NotificationService service) {
        this.service = service;
    }

    public reactor.core.publisher.Mono<ServerResponse> stream(ServerRequest request) {
        // stream the mine() flux as server-sent events filtered to current user
        Flux<NotificationResponse> events = service.streamForCurrentUser();
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(events, NotificationResponse.class);
    }
}
