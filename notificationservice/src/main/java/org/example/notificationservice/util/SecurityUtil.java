package org.example.notificationservice.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

public final class SecurityUtil{
private SecurityUtil(){}
public static Mono<Long> userId(){return auth().map(a->Long.valueOf(a.getName()));}
public static Mono<String> role(){return auth().map(a->a.getAuthorities().iterator().next().getAuthority());}
public static Mono<Long> customerId(){return auth().flatMap(a->{Object v=a.getDetails(); return v instanceof Long l?Mono.just(l):Mono.empty();});}
public static Mono<Void> requireAdmin(){return role().filter("ROLE_ADMIN"::equals).switchIfEmpty(Mono.error(new org.example.notificationservice.exception.ForbiddenException("Admin access required"))).then();}
private static Mono<Authentication> auth(){return ReactiveSecurityContextHolder.getContext().map(c->c.getAuthentication()).filter(Authentication::isAuthenticated).switchIfEmpty(Mono.error(new org.example.notificationservice.exception.UnauthorizedException("Authentication required")));}
}