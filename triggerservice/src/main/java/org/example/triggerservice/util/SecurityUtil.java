package org.example.triggerservice.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

public final class SecurityUtil{
    private SecurityUtil(){}

    public static Mono<Long> getUserId(){
        return getAuthentication()
                .map(Authentication::getPrincipal)
                .map(String::valueOf)
                .flatMap(SecurityUtil::parseLong);
    }

    public static Mono<String> getEmail(){
        return getAuthentication().map(Authentication::getName);
    }

    public static Mono<String> getRole(){
        return getAuthentication()
                .flatMap(authentication->Mono.justOrEmpty(authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .findFirst()));
    }

    public static Mono<Long> getCustomerId(){
        return ReactiveSecurityContextHolder.getContext()
                .map(context->context.getAuthentication())
                .map(Authentication::getDetails)
                .flatMap(details->details instanceof Long value?Mono.just(value):Mono.empty());
    }

    private static Mono<Authentication> getAuthentication(){
        return ReactiveSecurityContextHolder.getContext()
                .map(context->context.getAuthentication())
                .filter(Authentication::isAuthenticated);
    }

    private static Mono<Long> parseLong(String value){
        try{
            return Mono.just(Long.parseLong(value));
        }catch(NumberFormatException exception){
            return Mono.empty();
        }
    }
}