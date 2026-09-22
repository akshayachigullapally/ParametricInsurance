package org.example.notificationservice.service;

import org.example.notificationservice.dto.NotificationResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class NotificationStream {
    private final Sinks.Many<NotificationResponse> sink =
            Sinks.many().multicast().onBackpressureBuffer();

    public void publish(NotificationResponse n) {
        sink.tryEmitNext(n);
    }

    public Flux<NotificationResponse> asFlux() {
        return sink.asFlux();
    }
}