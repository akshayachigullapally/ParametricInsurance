package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.NotificationResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Live feed of new notifications for the SSE endpoint.
 *
 * A "direct" multicast sink is used on purpose: it stays open when every browser
 * disconnects (page reload, closed tab, reconnect). The previous buffered sink
 * cancelled itself when its last subscriber left, after which no live notification
 * reached anyone until the service was restarted. It also does not hold events back
 * for the next subscriber: a live feed only needs what happens while you are
 * connected (the inbox endpoint returns the rest).
 */
@Slf4j
@Component
public class NotificationStream {
    private final Sinks.Many<NotificationResponse> sink =
            Sinks.many().multicast().directBestEffort();

    /** Synchronized: notifications are created on many threads, and a sink must be fed one at a time. */
    public synchronized void publish(NotificationResponse n) {
        Sinks.EmitResult result = sink.tryEmitNext(n);
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn("Could not push notification {} to live streams: {}", n.getId(), result);
        }
    }

    public Flux<NotificationResponse> asFlux() {
        return sink.asFlux();
    }
}
