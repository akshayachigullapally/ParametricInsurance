package org.example.productservice.router;

import org.example.productservice.handler.ProductHandler;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.*;

@Configuration
public class ProductRouter{
    @Bean
    public RouterFunction<ServerResponse> productRoutes(ProductHandler handler){
        return RouterFunctions.route()
                .path("/api/products",builder->builder
                        .GET("",handler::getProducts)
                        .GET("/category/{category}",handler::getByCategory)
                        .GET("/{productId}",handler::getProduct)
                        .POST("",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                handler::createProduct)
                        .PUT("/{productId}",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                handler::updateProduct)
                        .PUT("/{productId}/status",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                handler::updateStatus)
                        .DELETE("/{productId}",handler::deleteProduct)
                        .GET("/admin/all",handler::getAllProducts)
                        .GET("/admin/measurement-status",handler::getMeasurementStatus)
                        .GET("/admin/{productId}",handler::getAdminProduct)
                        .GET("/admin/{productId}/threshold-history",handler::getThresholdHistory)
                        .PUT("/admin/{productId}/threshold",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                handler::updateThreshold)
                )
                .GET("/internal/products/code/{productCode}",handler::getInternalProductByCode)
                .GET("/internal/products/{productId}",handler::getInternalProduct)
                .POST("/internal/products/verified-event",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        handler::processVerifiedEvent)
                .build();
    }

}
