package org.example.financialservice;

import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

public final class TestAuth{
    private TestAuth(){}

    public static <T> Mono<T> admin(Mono<T> source, Long userId){
        return context(source,userId,"ROLE_ADMIN",null);
    }

    public static <T> Mono<T> customer(
            Mono<T> source,
            Long userId,
            Long customerId
    ){
        return context(source,userId,"ROLE_CUSTOMER",customerId);
    }

    public static <T> Mono<T> claimsOfficer(
            Mono<T> source,
            Long userId
    ){
        return context(source,userId,"ROLE_CLAIMS_OFFICER",null);
    }

    public static <T> Mono<T> underwriter(
            Mono<T> source,
            Long userId
    ){
        return context(source,userId,"ROLE_UNDERWRITER",null);
    }

    public static <T> Mono<T> context(
            Mono<T> source,
            Long userId,
            String role,
            Long customerId
    ){
        UsernamePasswordAuthenticationToken authentication=
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(userId),
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                );

        if(customerId!=null){
            authentication.setDetails(customerId);
        }

        return source.contextWrite(
                ReactiveSecurityContextHolder
                        .withAuthentication(authentication)
        );
    }
}