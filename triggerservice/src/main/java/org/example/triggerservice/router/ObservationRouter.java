package org.example.triggerservice.router;

import org.example.triggerservice.handler.ObservationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class ObservationRouter{
    @Bean
    public RouterFunction<ServerResponse> observationRoutes(
            ObservationHandler handler
    ){
        return route()
                .path("/api/triggers/observations",builder->builder
                        .GET("",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getAll)
                        .GET("/zone/{zoneId}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getByZoneId)
                        .GET("/measurement/{measurementType}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getByMeasurementType)
                        .GET("/{observationId}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getById)
                        .POST("",
                                contentType(MediaType.APPLICATION_JSON),
                                handler::create))
                .build();
    }
}