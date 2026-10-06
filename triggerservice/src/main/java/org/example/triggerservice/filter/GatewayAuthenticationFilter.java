package org.example.triggerservice.filter;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.triggerservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayAuthenticationFilter implements WebFilter{
    @Value("${gateway.internal-key:cityshield-local-gateway-key}")
    private String internalKey;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        String path=exchange.getRequest().getPath().value();

        if(isIgnoredPath(path)) return chain.filter(exchange);

        String receivedKey=exchange.getRequest().getHeaders().getFirst(GatewayHeaders.GATEWAY_KEY);
        if(!StringUtils.hasText(receivedKey)||!internalKey.equals(receivedKey)){
            log.warn("Blocked request without valid gateway key: {} {}",
                    exchange.getRequest().getMethod(),path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        if(path.startsWith("/internal/triggers/")){
            return chain.filter(exchange);
        }
        if(isInternalPath(path)) return chain.filter(exchange);

        String userId=exchange.getRequest().getHeaders().getFirst(GatewayHeaders.USER_ID);
        String email=exchange.getRequest().getHeaders().getFirst(GatewayHeaders.USER_EMAIL);
        String role=exchange.getRequest().getHeaders().getFirst(GatewayHeaders.USER_ROLE);
        String customerId=exchange.getRequest().getHeaders().getFirst(GatewayHeaders.CUSTOMER_ID);

        if(!StringUtils.hasText(userId)||!StringUtils.hasText(email)||!StringUtils.hasText(role)){
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        Long parsedCustomerId=parseLong(customerId);
        UsernamePasswordAuthenticationToken authentication=
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                );

        authentication.setDetails(parsedCustomerId);

        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private boolean isIgnoredPath(String path){
        return path.equals("/actuator")||
                path.startsWith("/actuator/");
    }

    private boolean isInternalPath(String path){
        return path.startsWith("/internal/triggers/");
    }

    private Long parseLong(String value){
        if(!StringUtils.hasText(value)) return null;
        try{
            return Long.parseLong(value);
        }catch(NumberFormatException exception){
            return null;
        }
    }
}