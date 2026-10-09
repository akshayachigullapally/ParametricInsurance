package org.example.notificationservice.handler;

import org.example.notificationservice.dto.InternalNotificationRequest;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.service.NotificationService;
import org.example.notificationservice.util.RequestValidationUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class NotificationHandler {

    private final NotificationService service;
    private final RequestValidationUtil validation;

    public NotificationHandler(NotificationService service, RequestValidationUtil validation) {
        this.service = service;
        this.validation = validation;
    }

    public Mono<ServerResponse> create(ServerRequest r) {
        return r.bodyToMono(InternalNotificationRequest.class)
                .map(validation::validate)
                .flatMap(service::create)
                .flatMap(x -> ServerResponse.status(HttpStatus.CREATED).bodyValue(x));
    }

    public Mono<ServerResponse> mine(ServerRequest r) {
        return ServerResponse.ok().body(service.mine(false), Object.class);
    }

    public Mono<ServerResponse> unread(ServerRequest r) {
        return ServerResponse.ok().body(service.mine(true), Object.class);
    }

    public Mono<ServerResponse> count(ServerRequest r) {
        return service.unreadCount().flatMap(x -> ServerResponse.ok().bodyValue(x));
    }

    public Mono<ServerResponse> stream(ServerRequest r) {
        Flux<ServerSentEvent<NotificationResponse>> sse = Flux.merge(
                service.stream().map(n -> ServerSentEvent.<NotificationResponse>builder(n).build()),
                // First comment right away so the browser sees the stream open at once,
                // then every 20 s to keep proxies from closing an idle connection.
                Flux.interval(Duration.ZERO, Duration.ofSeconds(20))
                        .map(i -> ServerSentEvent.<NotificationResponse>builder().comment("keep-alive").build()));
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(sse, ServerSentEvent.class);
    }

    public Mono<ServerResponse> read(ServerRequest r) {
        return service.markRead(id(r)).flatMap(x -> ServerResponse.ok().bodyValue(x));
    }

    public Mono<ServerResponse> adminRead(ServerRequest r) {
        return service.adminMarkRead(id(r)).flatMap(x -> ServerResponse.ok().bodyValue(x));
    }

    public Mono<ServerResponse> adminDelete(ServerRequest r) {
        return service.adminDelete(id(r)).flatMap(x -> ServerResponse.ok().bodyValue(x));
    }

    public Mono<ServerResponse> readAll(ServerRequest r) {
        return service.markAllRead().flatMap(x -> ServerResponse.ok().bodyValue(x));
    }

    public Mono<ServerResponse> delete(ServerRequest r) {
        return service.delete(id(r)).flatMap(x -> ServerResponse.ok().bodyValue(x));
    }

    public Mono<ServerResponse> all(ServerRequest r) {
        return ServerResponse.ok().body(service.all(), Object.class);
    }

    private Long id(ServerRequest r) {
        try {
            return Long.valueOf(r.pathVariable("notificationId"));
        } catch (Exception e) {
            throw new ValidationException("Invalid notificationId");
        }
    }
}
