package org.example.claimsservice.router;

import org.example.claimsservice.handler.ClaimHandler;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.function.server.*;

@Configuration
public class ClaimRouter{
    @Bean
    public RouterFunction<ServerResponse> claimRoutes(ClaimHandler handler){
        return RouterFunctions.route()
                .GET("/api/claims/admin/all",handler::getAllClaims)
                .GET("/api/claims/admin/{claimId}",handler::getClaim)
                .GET("/api/claims/review/manual",
                        handler::getManualClaims)
                .GET("/api/claims/review/manual/{claimId:[0-9]+}",
                        handler::getManualClaim)
                .GET("/api/claims/review/submitted",
                        handler::getSubmittedClaims)
                .GET("/api/claims/review/history",handler::getReviewHistory)
                .GET("/api/claims/review/{claimId}",handler::getClaim)
                .GET("/api/claims/me",handler::getMyClaims)
                .GET("/api/claims/{claimId}",handler::getMyClaim)
                .POST("/api/claims",handler::createClaim)
                .PUT("/api/claims/{claimId}/cancel",handler::cancelClaim)
                .PUT("/api/claims/{claimId}/start-review",
                        handler::startReview)
                .PUT("/api/claims/{claimId}/approve",
                        handler::approveClaim)
                .PUT("/api/claims/{claimId}/reject",
                        handler::rejectClaim)
                .PUT("/api/claims/{claimId}/settle",
                        handler::settleClaim)
                .GET("/api/claims/{claimId}/timeline",
                        handler::getMyClaimTimeline)
                .GET("/api/claims/admin/{claimId}/timeline",
                        handler::getClaimTimeline)
                .GET("/internal/claims/{claimId}",
                        handler::getInternalClaim)
                .POST("/internal/claims", handler::createInternalClaim)
                .POST("/internal/claims/from-trigger-event", handler::createClaimsFromTriggerEvent)
                .PUT("/internal/claims/{claimId}/settle", handler::settleInternal)
                .build();
    }
}