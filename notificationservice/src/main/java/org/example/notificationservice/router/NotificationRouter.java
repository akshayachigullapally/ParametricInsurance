package org.example.notificationservice.router;

import org.example.notificationservice.handler.NotificationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Configuration
public class NotificationRouter {

    @Bean
    RouterFunction<ServerResponse> notificationRoutes(NotificationHandler n) {
        return RouterFunctions.route()
                // Internal endpoint (gateway key only): other services create notifications here
                .POST("/internal/notifications", accept(MediaType.APPLICATION_JSON), n::create)

                // Signed-in user's inbox and live stream
                .GET("/api/notifications/me", n::mine)
                .GET("/api/notifications/me/unread", n::unread)
                .GET("/api/notifications/me/unread-count", n::count)
                .GET("/api/notifications/me/stream", n::stream)
                .PUT("/api/notifications/{notificationId}/read", n::read)
                .PUT("/api/notifications/me/read-all", n::readAll)
                .DELETE("/api/notifications/{notificationId}", n::delete)

                // Admin
                .GET("/api/notifications/admin/all", n::all)
                .PUT("/api/notifications/admin/{notificationId}/read", n::adminRead)
                .DELETE("/api/notifications/admin/{notificationId}", n::adminDelete)
                .build();
    }
}
