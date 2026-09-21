package org.example.triggerservice.router;

import org.example.triggerservice.handler.ObservationValidationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class ObservationValidationRouter{
    @Bean
    public RouterFunction<ServerResponse> observationValidationRoutes(
            ObservationValidationHandler handler
    ){
        return route()
                .path("/api/triggers/observations/validation",builder->builder
                        .GET("/received",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getReceived)
                        .PUT("/{observationId}/validate",
                                handler::validate)
                        .PUT("/{observationId}/reject",
                                contentType(MediaType.APPLICATION_JSON),
                                handler::reject)
                        .POST("/validate-all",
                                handler::validateAll))
                .build();
    }
}
