package org.example.productservice.router;

import org.example.productservice.handler.PolicyLifecycleHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class PolicyLifecycleRouter{
    @Bean
    public RouterFunction<ServerResponse> policyLifecycleRoutes(PolicyLifecycleHandler handler){
        return RouterFunctions.route()
                .POST("/api/policies/{policyId}/renew",handler::renew)
                .PUT("/api/policies/{policyId}/auto-renew",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        handler::autoRenew)
                .GET("/api/policies/{policyId}/certificate",handler::customerCertificate)
                .GET("/api/policies/admin/{policyId}/certificate",handler::staffCertificate)
                .build();
    }
}
