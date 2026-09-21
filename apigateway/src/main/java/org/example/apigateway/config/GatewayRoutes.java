package org.example.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;

@Configuration
public class GatewayRoutes{
    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder){
        return builder.routes()
                .route("auth-service-route",r->r
                        .path("/api/auth/**","/api/users/**")
                        .uri("lb://AUTH-SERVICE"))
                .route("product-policy-service-route",r->r
                        .path(
                                "/api/products/**",
                                "/api/policies/**",
                                "/internal/products/**",
                                "/internal/policies/**"
                        )
                        .uri("lb://PRODUCT-SERVICE"))
                .route("claims-service-route",r->r
                        .path("/api/claims/**")
                        .uri("lb://CLAIMS-SERVICE"))
                .route("trigger-service-route",r->r
                        .path("/api/triggers/**")
                        .uri("lb://TRIGGER-SERVICE"))
                .route("notification-service-route",r->r
                        .path("/api/notifications/**")
                        .uri("lb://NOTIFICATION-SERVICE"))
                .route("financial-service-route",r->r
                        .path("/api/financial/**")
                        .uri("lb://FINANCIAL-SERVICE"))
                .build();
    }
}