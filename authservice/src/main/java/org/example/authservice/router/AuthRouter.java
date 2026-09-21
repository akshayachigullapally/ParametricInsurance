package org.example.authservice.router;

import org.example.authservice.handler.AuthHandler;
import org.example.authservice.handler.EmailVerificationHandler;
import org.example.authservice.handler.PasswordResetHandler;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.*;

@Configuration
public class AuthRouter{
    @Bean
    public RouterFunction<ServerResponse> authRoutes(
            AuthHandler handler,
            EmailVerificationHandler emailHandler,
            PasswordResetHandler passwordHandler){

        return RouterFunctions.route()
                .path("/api/auth",builder->builder
                        .POST("/register",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                handler::register)
                        .POST("/login",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                handler::login)
                        .POST("/refresh",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                handler::refresh)
                        .POST("/logout",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                handler::logout)
                        .GET("/me",
                                RequestPredicates.accept(MediaType.APPLICATION_JSON),
                                handler::me)
                        .GET("/sessions",
                                RequestPredicates.accept(MediaType.APPLICATION_JSON),
                                handler::sessions)
                        .DELETE("/sessions/{sessionId}",handler::revokeSession)
                        .POST("/verify-email",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                emailHandler::verify)
                        .POST("/verify-email/resend",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                emailHandler::resend)
                        .POST("/verify-email/resend-by-email",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                emailHandler::resendByEmail)
                        .POST("/forgot-password",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                passwordHandler::forgotPassword)
                        .POST("/reset-password",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                passwordHandler::resetPassword)
                )
                .build();
    }
}
