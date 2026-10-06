package org.example.authservice.filter;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.authservice.util.GatewayHeaders;
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

    public GatewayAuthenticationFilter(@Value("${gateway.internal-key:cityshield-local-gateway-key}") String internalGatewayKey){
        this.internalGatewayKey=internalGatewayKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        String path=exchange.getRequest().getPath().value();

        if(isPublicInfrastructurePath(path)) return chain.filter(exchange);

        String gatewayKey=exchange.getRequest().getHeaders()
                .getFirst(GatewayHeaders.GATEWAY_KEY);

        if(!internalGatewayKey.equals(gatewayKey)){
            log.warn("Blocked request without valid gateway key: {} {}",
                    exchange.getRequest().getMethod(),path);
            return forbidden(exchange,"Request must pass through API Gateway");
        }

        String userId=exchange.getRequest().getHeaders()
                .getFirst(GatewayHeaders.USER_ID);
        String email=exchange.getRequest().getHeaders()
                .getFirst(GatewayHeaders.USER_EMAIL);
        String role=exchange.getRequest().getHeaders()
                .getFirst(GatewayHeaders.USER_ROLE);
        String customerId=exchange.getRequest().getHeaders()
                .getFirst(GatewayHeaders.CUSTOMER_ID);

        if(customerId!=null&&!customerId.isBlank()){
            try{
                exchange.getAttributes().put(
                        GatewayHeaders.AUTHENTICATED_CUSTOMER_ID,
                        Long.valueOf(customerId)
                );
            }catch(NumberFormatException e){
                return forbidden(exchange,"Invalid authenticated customer id");
            }
        }

        if(userId==null||userId.isBlank()||email==null||email.isBlank()||
                role==null||role.isBlank()){
            return chain.filter(exchange);
        }

        Long authenticatedUserId;
        try{
            authenticatedUserId=Long.valueOf(userId);
        }catch(NumberFormatException e){
            return forbidden(exchange,"Invalid authenticated user id");
        }

        exchange.getAttributes().put(
                GatewayHeaders.AUTHENTICATED_USER_ID,
                authenticatedUserId
        );

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

    private boolean isPublicInfrastructurePath(String path){
        return path.equals("/actuator")||
                path.startsWith("/actuator/")||
                path.startsWith("/api/auth/");
    }

    private Mono<Void> forbidden(ServerWebExchange exchange,String message){
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        String json="{\"status\":403,\"error\":\"Forbidden\",\"message\":\""+
                escape(message)+"\"}";
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer=exchange.getResponse().bufferFactory().wrap(bytes);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String escape(String value){
        return value.replace("\\","\\\\").replace("\"","\\\"");
    }
}