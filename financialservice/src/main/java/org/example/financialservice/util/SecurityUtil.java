package org.example.financialservice.util;

import org.example.financialservice.exception.ForbiddenException;
import org.example.financialservice.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public final class SecurityUtil{
    private SecurityUtil(){}

    public static Mono<Long> userId(){
        return authentication()
                .map(authentication->Long.valueOf(authentication.getName()));
    }

    public static Mono<Long> customerId(){
        return authentication()
                .flatMap(authentication->{
                    Object details=authentication.getDetails();

                    if(details instanceof Long customerId){
                        return Mono.just(customerId);
                    }

                    return Mono.error(
                            new ForbiddenException("Customer profile is required")
                    );
                });
    }

    public static Mono<String> role(){
        return authentication()
                .map(authentication->
                        authentication.getAuthorities()
                                .iterator()
                                .next()
                                .getAuthority()
                );
    }

    public static Mono<Long> requireCustomer(){
        return Mono.zip(role(),customerId())
                .filter(tuple->"ROLE_CUSTOMER".equals(
                        tuple.getT1()
                ))
                .switchIfEmpty(
                        Mono.error(
                                new ForbiddenException(
                                        "Customer access required"
                                )
                        )
                )
                .map(Tuple2::getT2);
    }

    public static Mono<Void> requireAdmin(){
        return role()
                .filter("ROLE_ADMIN"::equals)
                .switchIfEmpty(
                        Mono.error(new ForbiddenException("Admin access required"))
                )
                .then();
    }

    public static Mono<Void> requireClaimsOfficerOrAdmin(){
        return role()
                .filter(role->
                        "ROLE_ADMIN".equals(role)||
                                "ROLE_CLAIMS_OFFICER".equals(role)
                )
                .switchIfEmpty(
                        Mono.error(
                                new ForbiddenException(
                                        "Claims officer or Admin access required"
                                )
                        )
                )
                .then();
    }

    private static Mono<Authentication> authentication(){
        return ReactiveSecurityContextHolder.getContext()
                .map(context->context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .switchIfEmpty(
                        Mono.error(
                                new UnauthorizedException("Authentication required")
                        )
                );
    }
}