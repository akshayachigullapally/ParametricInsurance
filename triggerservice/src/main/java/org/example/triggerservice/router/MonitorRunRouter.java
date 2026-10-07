package org.example.triggerservice.router;

import org.example.triggerservice.handler.MonitorRunHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class MonitorRunRouter{
    @Bean
    public RouterFunction<ServerResponse> monitorRunRoutes(MonitorRunHandler handler){
        return route()
                .path("/api/triggers/monitor-runs",builder->builder
                        .GET("",accept(MediaType.APPLICATION_JSON),handler::getAll)
                        .GET("/{runId}",accept(MediaType.APPLICATION_JSON),handler::getById)
                        .POST("/scan",handler::triggerScan))
                .build();
    }
}
