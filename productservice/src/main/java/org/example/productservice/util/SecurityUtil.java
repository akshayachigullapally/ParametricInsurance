package org.example.productservice.util;

import org.example.productservice.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

public final class SecurityUtil{
    private SecurityUtil(){}

    public static Mono<Long> getUserId(ServerRequest request){
        return request.principal()
                .cast(Authentication.class)
                .map(Authentication::getPrincipal)
                .flatMap(SecurityUtil::convertUserId)
                .switchIfEmpty(Mono.error(
                        new ForbiddenException("Authentication is required")
                ));
    }

    public static Mono<String> getRole(ServerRequest request){
        return request.principal()
                .cast(Authentication.class)
                .flatMap(authentication->{
                    if(authentication.getAuthorities().isEmpty()){
                        return Mono.error(
                                new ForbiddenException("Authenticated role is required")
                        );
                    }

                    return Mono.just(
                            authentication.getAuthorities()
                                    .iterator()
                                    .next()
                                    .getAuthority()
                    );
                });
    }

    private static Mono<Long> convertUserId(Object principal){
        if(principal instanceof Long value) return Mono.just(value);
        if(principal instanceof Number value) return Mono.just(value.longValue());

        if(principal instanceof String value){
            try{
                return Mono.just(Long.valueOf(value));
            }catch(NumberFormatException e){
                return Mono.error(
                        new ForbiddenException("Invalid authenticated user id")
                );
            }
        }

        return Mono.error(
                new ForbiddenException("Invalid authenticated principal")
        );
    }
}