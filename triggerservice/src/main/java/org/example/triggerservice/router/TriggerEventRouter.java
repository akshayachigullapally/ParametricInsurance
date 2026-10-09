package org.example.triggerservice.router;

import org.example.triggerservice.handler.TriggerEventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class TriggerEventRouter{
    @Bean
    public RouterFunction<ServerResponse> triggerEventRoutes(
            TriggerEventHandler handler
    ){
        return route()
                .path("/api/triggers/events",builder->builder
                        .GET("",accept(MediaType.APPLICATION_JSON),
                                handler::getAll)
                        .GET("/{eventId}",accept(MediaType.APPLICATION_JSON),
                                handler::getById))
                // Financial service marks an event processed once its payouts are done.
                .PUT("/internal/triggers/events/{eventId}/processed",
                        handler::markProcessed)
                .build();
    }
}
