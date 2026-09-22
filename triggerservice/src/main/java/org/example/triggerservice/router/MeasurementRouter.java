package org.example.triggerservice.router;

import org.example.triggerservice.handler.MeasurementHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

@Configuration
public class MeasurementRouter{
    @Bean
    public RouterFunction<ServerResponse> measurementRoutes(
            MeasurementHandler handler
    ){
        return route()
                .path("/api/triggers/measurements",builder->builder
                        .GET("/types",accept(MediaType.APPLICATION_JSON),
                                handler::getMeasurementTypes)
                        .GET("/current",accept(MediaType.APPLICATION_JSON),
                                handler::getCurrentMeasurements))
                .PUT("/api/triggers/current-measurements",
                        contentType(MediaType.APPLICATION_JSON),
                        handler::processCurrentMeasurement)
                .GET("/api/triggers/current-measurements",
                        accept(MediaType.APPLICATION_JSON),
                        handler::getCurrentMeasurementSnapshots)
                .path("/internal/triggers/measurements",builder->builder
                        .GET("/current",accept(MediaType.APPLICATION_JSON),
                                handler::getCurrentMeasurementsInternal))
                .build();
    }
}