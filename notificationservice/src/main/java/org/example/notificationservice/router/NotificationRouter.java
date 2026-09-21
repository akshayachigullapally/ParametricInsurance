package org.example.notificationservice.router;

import org.example.notificationservice.handler.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Configuration
public class NotificationRouter {

    @Bean
    RouterFunction<ServerResponse> notificationRoutes(
            NotificationHandler n,
            AuditEventHandler a,
            NotificationTemplateHandler t,
            NotificationPreferenceHandler p,
            DeliveryAttemptHandler d,
            org.example.notificationservice.handler.NotificationSseHandler sse) {

        return RouterFunctions.route()
                // Internal endpoints (gateway key only)
                .POST("/internal/notifications", accept(org.springframework.http.MediaType.APPLICATION_JSON), n::create)
                .POST("/internal/audit-events", accept(org.springframework.http.MediaType.APPLICATION_JSON), a::create)

                // User-facing notification endpoints
                .GET("/api/notifications/me", n::mine)
                .GET("/api/notifications/me/unread", n::unread)
                .GET("/api/notifications/me/unread-count", n::count)
                .GET("/api/notifications/me/stream", org.springframework.web.reactive.function.server.RequestPredicates.accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM), sse::stream)
                .PUT("/api/notifications/{notificationId}/read", n::read)
                .PUT("/api/notifications/me/read-all", n::readAll)
                .DELETE("/api/notifications/{notificationId}", n::delete)

                // Send notifications from default templates
                .POST("/api/notifications/from-template", accept(org.springframework.http.MediaType.APPLICATION_JSON), n::fromTemplate)

                // User notification preferences
                .GET("/api/notifications/me/preferences", p::myPreferences)
                .PUT("/api/notifications/me/preferences", p::upsert)

                // Delivery attempts for a notification
                .GET("/api/notifications/{notificationId}/delivery-attempts", d::getByNotification)

                // Template management (admin)
                .GET("/api/notifications/admin/templates", t::all)
                .POST("/api/notifications/admin/templates", accept(org.springframework.http.MediaType.APPLICATION_JSON), t::create)
                .GET("/api/notifications/admin/templates/{templateId}", t::get)
                .PUT("/api/notifications/admin/templates/{templateId}", accept(org.springframework.http.MediaType.APPLICATION_JSON), t::update)
                .PUT("/api/notifications/admin/templates/{templateId}/deactivate", t::deactivate)
                .PUT("/api/notifications/admin/templates/{templateId}/activate", t::activate)
                .GET("/api/notifications/admin/templates/defaults", t::defaults)

                // Admin endpoints
                .GET("/api/notifications/admin/all", n::all)
                .GET("/api/notifications/admin/failed", n::failed)
                .GET("/api/notifications/admin/audit", a::all)
                .POST("/api/notifications/admin/batch", accept(org.springframework.http.MediaType.APPLICATION_JSON), n::batch)

                .build();
    }
}
