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
                        .GET("/status/{status}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getByStatus)
                        .GET("/definition/{definitionId}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getByDefinitionId)
                        .GET("/product/{productId}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getByProductId)
                        .GET("/zone/{zoneId}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getByZoneId)
                        .GET("/{eventId}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getById)
                        .PUT("/{eventId}/start-validation",
                                handler::startValidation)
                        .PUT("/{eventId}/verify",
                                handler::verify)
                        .PUT("/{eventId}/reject",
                                handler::reject)
                        .PUT("/{eventId}/process",
                                handler::process))
                .PUT(
                        "/internal/triggers/events/{eventId}/processed",
                        handler::markProcessed)
                .build();
    }
}