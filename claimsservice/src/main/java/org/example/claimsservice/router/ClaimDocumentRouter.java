package org.example.claimsservice.router;

import org.example.claimsservice.handler.ClaimDocumentHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ClaimDocumentRouter{
    @Bean
    public RouterFunction<ServerResponse> claimDocumentRoutes(ClaimDocumentHandler handler){
        return RouterFunctions.route()
                .POST("/api/claims/{claimId}/documents",
                        RequestPredicates.contentType(MediaType.MULTIPART_FORM_DATA),
                        handler::upload)
                .GET("/api/claims/{claimId}/documents",handler::listMine)
                .GET("/api/claims/{claimId}/documents/{documentId}",handler::downloadMine)
                .GET("/api/claims/review/{claimId}/documents",handler::listForReview)
                .GET("/api/claims/review/{claimId}/documents/{documentId}",handler::downloadForReview)
                .build();
    }
}
