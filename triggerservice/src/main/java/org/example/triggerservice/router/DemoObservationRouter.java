package org.example.triggerservice.router;

import org.example.triggerservice.handler.DemoObservationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class DemoObservationRouter{
    @Bean
    public RouterFunction<ServerResponse> demoObservationRoutes(
            DemoObservationHandler handler
    ){
        return route()
                .path("/api/triggers/demo",builder->builder
                        .POST("/observations",
                                contentType(MediaType.APPLICATION_JSON),
                                handler::recordAndEvaluate))
                .build();
    }
}