package org.example.productservice.router;

import org.example.productservice.handler.PolicyHandler;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.function.server.*;

@Configuration
public class PolicyRouter{
    @Bean
    public RouterFunction<ServerResponse> policyRoutes(
            PolicyHandler handler){

        return RouterFunctions.route()
                .GET(
                        "/internal/policies/{policyId}",
                        handler::getInternalPolicy
                )
                .GET(
                        "/api/policies/zones/active",
                        handler::getActiveZones
                )
                .GET(
                        "/api/policies/admin/all",
                        handler::getAllPolicies
                )
                .GET(
                        "/api/policies/admin/zone-exposure",
                        handler::getZoneExposure
                )
                .GET(
                        "/api/policies/admin/{policyId}",
                        handler::getPolicy
                )
                .GET(
                        "/api/policies/review/pending",
                        handler::getPendingPolicies
                )
                .GET(
                        "/api/policies/review/history",
                        handler::getReviewHistory
                )
                .GET(
                        "/api/policies/me",
                        handler::getMyPolicies
                )
                .GET(
                        "/api/policies/{policyId}",
                        handler::getMyPolicy
                )
                .POST(
                        "/api/policies",
                        handler::createPolicy
                )
                .POST(
                        "/api/policies/custom",
                        handler::createCustomPolicy
                )
                .PUT(
                        "/api/policies/{policyId}/cancel",
                        handler::cancelPolicy
                )
                .PUT(
                        "/api/policies/{policyId}/approve",
                        handler::approvePolicy
                )
                .PUT(
                        "/api/policies/{policyId}/reject",
                        handler::rejectPolicy
                )
                .GET(
                        "/api/policies/{policyId}/history",
                        handler::getPolicyHistory
                )
                .GET(
                        "/api/policies/{policyId}/snapshot",
                        handler::getPolicySnapshot
                )
                .build();
    }
}
