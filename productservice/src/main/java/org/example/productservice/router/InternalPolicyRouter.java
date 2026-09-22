package org.example.productservice.router;

import org.example.productservice.handler.InternalPolicyHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class InternalPolicyRouter{
    @Bean
    public RouterFunction<ServerResponse> internalPolicyRoutes(
            InternalPolicyHandler handler
    ){
        return route()
                .GET(
                        "/internal/policies/active-for-trigger",
                        accept(MediaType.APPLICATION_JSON),
                        handler::getEligiblePolicies)
                .build();
    }
}