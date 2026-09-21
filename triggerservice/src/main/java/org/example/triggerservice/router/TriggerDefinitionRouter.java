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
                        .GET("",accept(MediaType.APPLICATION_JSON),handler::getAll)
                        .GET("/active",accept(MediaType.APPLICATION_JSON),handler::getActive)
                        .GET("/peril/{perilId}",accept(MediaType.APPLICATION_JSON),handler::getByPerilId)
                        .GET("/zone/{zoneId}",accept(MediaType.APPLICATION_JSON),handler::getByZoneId)
                        .GET("/product/{productId}",accept(MediaType.APPLICATION_JSON),handler::getByProductId)
                        .GET("/{definitionId}",accept(MediaType.APPLICATION_JSON),handler::getById)
                        .POST("",contentType(MediaType.APPLICATION_JSON),handler::create)
                        .PUT("/{definitionId}",contentType(MediaType.APPLICATION_JSON),handler::update)
                        .PUT("/{definitionId}/status",contentType(MediaType.APPLICATION_JSON),handler::updateStatus)
                        .DELETE("/{definitionId}",handler::archive))
                .build();
    }
}