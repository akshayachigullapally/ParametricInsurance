package org.example.authservice.router;

import org.example.authservice.handler.UserHandler;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.*;

@Configuration
public class UserRouter{
    @Bean
    public RouterFunction<ServerResponse> userRoutes(UserHandler handler){
        return RouterFunctions.route()
                .path("/api/users",builder->builder
                        .GET("/me",handler::getMyProfile)
                        .PUT("/me",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                handler::saveMyProfile)
                        .DELETE("/me",handler::deactivateMyProfile)
                        .GET("",handler::getAllUsers)
                        .GET("/{userId}",handler::getUser)
                        .PUT("/{userId}/status",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                handler::updateStatus)
                )
                .build();
    }
}