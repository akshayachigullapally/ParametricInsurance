package org.example.triggerservice.router;

import org.example.triggerservice.handler.TriggerDefinitionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class TriggerDefinitionRouter{
    @Bean
    public RouterFunction<ServerResponse> triggerDefinitionRoutes(
            TriggerDefinitionHandler handler
    ){
        return route()
                .path("/api/triggers/definitions",builder->builder
                        .GET("/{definitionId}",accept(MediaType.APPLICATION_JSON),handler::getById))
                .path("/internal/triggers/products",builder->builder
                        .PUT("/{productId}/definition",
                                contentType(MediaType.APPLICATION_JSON),
                                handler::syncProductDefinition))
                .build();
    }
}