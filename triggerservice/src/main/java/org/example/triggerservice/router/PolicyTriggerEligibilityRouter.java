package org.example.triggerservice.router;

import org.example.triggerservice.handler.PolicyTriggerEligibilityHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class PolicyTriggerEligibilityRouter{
    @Bean
    public RouterFunction<ServerResponse> policyTriggerEligibilityRoutes(
            PolicyTriggerEligibilityHandler handler
    ){
        return route()
                .GET(
                        "/internal/triggers/policy/{policyId}/eligibility",
                        accept(MediaType.APPLICATION_JSON),
                        handler::checkEligibility
                )
                .build();
    }
}