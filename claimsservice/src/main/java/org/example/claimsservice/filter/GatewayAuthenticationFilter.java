package org.example.claimsservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.example.claimsservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class GatewayAuthenticationFilter implements WebFilter{
    private final String internalGatewayKey;

    public GatewayAuthenticationFilter(@Value("${gateway.internal-key:climatesure-local-gateway-key}") String internalGatewayKey){
        this.internalGatewayKey=internalGatewayKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        String path=exchange.getRequest().getPath().value();

        if(path.equals("/actuator")||path.startsWith("/actuator/")){
            return chain.filter(exchange);
        }

        String key=exchange.getRequest().getHeaders()
                .getFirst(GatewayHeaders.GATEWAY_KEY);

        if(!internalGatewayKey.equals(key)){
            log.warn("Blocked request without valid gateway key: {} {}",
                    exchange.getRequest().getMethod(),path);
            return writeError(exchange,HttpStatus.FORBIDDEN,
                    "Request must pass through API Gateway");
        }

        String userId=exchange.getRequest().getHeaders()
                .getFirst(GatewayHeaders.USER_ID);
        String email=exchange.getRequest().getHeaders()
                .getFirst(GatewayHeaders.USER_EMAIL);
        String role=exchange.getRequest().getHeaders()
                .getFirst(GatewayHeaders.USER_ROLE);

        if(userId==null||userId.isBlank()||
                email==null||email.isBlank()||
                role==null||role.isBlank()){
            return chain.filter(exchange);
        }

        Long authenticatedUserId;

        try{
            authenticatedUserId=Long.valueOf(userId);
        }catch(NumberFormatException e){
            return writeError(exchange,HttpStatus.FORBIDDEN,
                    "Invalid authenticated user id");
        }

        String authority=role.startsWith("ROLE_")?role:"ROLE_"+role;

        UsernamePasswordAuthenticationToken authentication=
                new UsernamePasswordAuthenticationToken(
                        authenticatedUserId,
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                );

        authentication.setDetails(email);

        return chain.filter(exchange)
                .contextWrite(
                        ReactiveSecurityContextHolder.withAuthentication(authentication)
                );
    }

    private Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            String message){

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        String json="{\"status\":"+status.value()+
                ",\"error\":\""+status.getReasonPhrase()+
                "\",\"message\":\""+message.replace("\"","\\\"")+"\"}";

        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer=exchange.getResponse()
                .bufferFactory().wrap(bytes);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}