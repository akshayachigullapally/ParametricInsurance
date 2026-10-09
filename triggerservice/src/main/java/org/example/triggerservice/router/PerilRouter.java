package org.example.triggerservice.router;

import org.example.triggerservice.handler.PerilHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class PerilRouter{
    @Bean
    public RouterFunction<ServerResponse> perilRoutes(PerilHandler handler){
        return route()
                .path("/api/triggers/perils",builder->builder
                        .GET("",accept(MediaType.APPLICATION_JSON),handler::getAll)
                        .GET("/archived",accept(MediaType.APPLICATION_JSON),handler::getArchived)
                        .GET("/{perilId}",accept(MediaType.APPLICATION_JSON),handler::getById)
                        .POST("",contentType(MediaType.APPLICATION_JSON),handler::create)
                        .PUT("/{perilId}",contentType(MediaType.APPLICATION_JSON),handler::update)
                        .PUT("/{perilId}/status",contentType(MediaType.APPLICATION_JSON),handler::updateStatus)
                        .PUT("/{perilId}/restore",handler::restore)
                        .DELETE("/{perilId}",handler::archive))
                .build();
    }
}