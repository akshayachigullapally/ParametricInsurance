package org.example.triggerservice.router;

import org.example.triggerservice.handler.TriggerEvaluationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class TriggerEvaluationRouter{
    @Bean
    public RouterFunction<ServerResponse> triggerEvaluationRoutes(
            TriggerEvaluationHandler handler
    ){
        return route()
                .path("/api/triggers/evaluations",builder->builder
                        .GET("",accept(MediaType.APPLICATION_JSON),
                                handler::getAll)
                        .GET("/definition/{definitionId}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getByDefinitionId)
                        .GET("/{evaluationId}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getById))
                .POST(
                        "/api/triggers/definitions/{definitionId}/evaluate",
                        contentType(MediaType.APPLICATION_JSON),
                        handler::evaluate)
                .build();
    }
}