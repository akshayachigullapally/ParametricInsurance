package org.example.notificationservice.handler;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.BatchNotificationRequest;
import org.example.notificationservice.dto.InternalNotificationRequest;
import org.example.notificationservice.dto.NotificationQuery;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.dto.SendFromTemplateRequest;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.service.NotificationService;
import org.example.notificationservice.util.RequestValidationUtil;
import org.example.notificationservice.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Slf4j
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

    public Mono<ServerResponse> fromTemplate(ServerRequest r) {
        return SecurityUtil.requireAdmin()
                .then(r.bodyToMono(SendFromTemplateRequest.class))
                .map(validation::validate)
                .flatMapMany(service::sendFromTemplate)
                .collectList()
                .flatMap(list -> ServerResponse.status(HttpStatus.CREATED).bodyValue(list));
    }

    public Mono<ServerResponse> batch(ServerRequest r) {
        return SecurityUtil.requireAdmin()
                .then(r.bodyToMono(BatchNotificationRequest.class))
                .map(validation::validate)
                .flatMap(req -> service.batch(req.getNotifications()).collectList())
                .flatMap(list -> ServerResponse.status(HttpStatus.CREATED).bodyValue(list));
    }

    public Mono<ServerResponse> mine(ServerRequest r) {
        NotificationQuery query = NotificationQuery.of(
                Boolean.parseBoolean(r.queryParam("unreadOnly").orElse("false")),
                parseNotificationType(r.queryParam("notificationType").orElse(null)),
                parseCreatedAfter(r.queryParam("createdAfter").orElse(null)),
                intParam(r, "page", 0),
                Math.max(1, intParam(r, "size", 20)),
                ascending(r.queryParam("sort").orElse("createdAt,desc")),
                r.queryParam("page").isPresent() || r.queryParam("size").isPresent());
        return ServerResponse.ok().body(service.mine(query), Object.class);
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
                Flux.interval(Duration.ofSeconds(20))
                        .map(i -> ServerSentEvent.<NotificationResponse>builder().comment("keep-alive").build()));
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(sse, ServerSentEvent.class);
    }

    public Mono<ServerResponse> read(ServerRequest r) {
        return service.markRead(id(r)).flatMap(x -> ServerResponse.ok().bodyValue(x));
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

    public Mono<ServerResponse> failed(ServerRequest r) {
        return ServerResponse.ok().body(service.failed(), Object.class);
    }

    public Mono<ServerResponse> byType(ServerRequest r) {
        String typeStr = r.pathVariable("type");
        NotificationType type;
        try {
            type = NotificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid notification type: " + typeStr);
        }
        return ServerResponse.ok().body(service.byType(type), Object.class);
    }

    private Long id(ServerRequest r) {
        try {
            return Long.valueOf(r.pathVariable("notificationId"));
        } catch (Exception e) {
            throw new ValidationException("Invalid notificationId");
        }
    }

    private NotificationType parseNotificationType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return NotificationType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid notificationType: " + value);
        }
    }

    private LocalDateTime parseCreatedAfter(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
            // not a bare local timestamp; fall through to offset/instant forms
        }
        try {
            return OffsetDateTime.parse(v).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw new ValidationException("Invalid createdAfter timestamp: " + value);
        }
    }

    private int intParam(ServerRequest r, String name, int defaultValue) {
        String value = r.queryParam(name).orElse(null);
        if (value == null || value.isBlank()) return defaultValue;
        try {
            int parsed = Integer.parseInt(value);
            return parsed < 0 ? 0 : parsed;
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid " + name + ": " + value);
        }
    }

    private boolean ascending(String sort) {
        String value = sort == null ? "" : sort.trim();
        if (value.contains(",")) value = value.substring(value.indexOf(',') + 1);
        return "asc".equalsIgnoreCase(value);
    }
}
