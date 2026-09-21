package org.example.claimsservice.util;

import org.example.claimsservice.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

public final class SecurityUtil{
    private SecurityUtil(){}

    public static Mono<Long> getUserId(ServerRequest request){
        return request.principal()
                .cast(Authentication.class)
                .map(Authentication::getPrincipal)
                .flatMap(SecurityUtil::convert)
                .switchIfEmpty(Mono.error(
                        new ForbiddenException("Authentication is required")
                ));
    }

    private static Mono<Long> convert(Object principal){
        if(principal instanceof Long value) return Mono.just(value);
        if(principal instanceof Number value) return Mono.just(value.longValue());

        try{
            return Mono.just(Long.valueOf(principal.toString()));
        }catch(Exception e){
            return Mono.error(
                    new ForbiddenException("Invalid authenticated user id")
            );
        }
    }
}