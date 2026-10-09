package org.example.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.UriSpec;
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
                                "/api/policies/**"
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
                // Actuator of each service through the gateway,
                // e.g. /auth/actuator/health -> AUTH-SERVICE /actuator/health
                .route("auth-actuator-route",r->actuator(r,"auth")
                        .uri("lb://AUTH-SERVICE"))
                .route("product-actuator-route",r->actuator(r,"product")
                        .uri("lb://PRODUCT-SERVICE"))
                .route("claims-actuator-route",r->actuator(r,"claims")
                        .uri("lb://CLAIMS-SERVICE"))
                .route("trigger-actuator-route",r->actuator(r,"trigger")
                        .uri("lb://TRIGGER-SERVICE"))
                .route("notification-actuator-route",r->actuator(r,"notification")
                        .uri("lb://NOTIFICATION-SERVICE"))
                .route("financial-actuator-route",r->actuator(r,"financial")
                        .uri("lb://FINANCIAL-SERVICE"))
                .route("eureka-actuator-route",r->actuator(r,"eureka")
                        .uri("http://localhost:8761"))
                .build();
    }

    private UriSpec actuator(PredicateSpec r,String prefix){
        return r.path("/"+prefix+"/actuator","/"+prefix+"/actuator/**")
                .filters(f->f.rewritePath(
                        "/"+prefix+"/actuator(?<rest>.*)",
                        "/actuator${rest}"
                ));
    }
}