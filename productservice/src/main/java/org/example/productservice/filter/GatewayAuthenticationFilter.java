package org.example.productservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.example.productservice.util.GatewayHeaders;
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
    private final boolean requireGatewayKey;

    public GatewayAuthenticationFilter(
            @Value("${gateway.internal-key}") String internalGatewayKey,
            @Value("${gateway.require-gateway-key:true}") boolean requireGatewayKey){

        this.internalGatewayKey=internalGatewayKey;
        this.requireGatewayKey=requireGatewayKey;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain){

        String path=exchange.getRequest().getPath().value();

        if(isActuatorPath(path)){
            return chain.filter(exchange);
        }

        String gatewayKey=exchange.getRequest()
                .getHeaders()
                .getFirst(GatewayHeaders.GATEWAY_KEY);

        if(requireGatewayKey && !internalGatewayKey.equals(gatewayKey)){
            log.warn("Blocked request without valid gateway key: {} {}",
                    exchange.getRequest().getMethod(),path);
            return forbidden(
                    exchange,
                    "Request must pass through API Gateway"
            );
        }

        if(isInternalPath(path)){
            return chain.filter(exchange);
        }

        String userId=exchange.getRequest()
                .getHeaders()
                .getFirst(GatewayHeaders.USER_ID);

        String email=exchange.getRequest()
                .getHeaders()
                .getFirst(GatewayHeaders.USER_EMAIL);

        String role=exchange.getRequest()
                .getHeaders()
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
            return forbidden(
                    exchange,
                    "Invalid authenticated user id"
            );
        }

        String authority=role.startsWith("ROLE_")
                ?role
                :"ROLE_"+role;

        UsernamePasswordAuthenticationToken authentication=
                new UsernamePasswordAuthenticationToken(
                        authenticatedUserId,
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                );

        authentication.setDetails(email);

        return chain.filter(exchange)
                .contextWrite(
                        ReactiveSecurityContextHolder.withAuthentication(
                                authentication
                        )
                );
    }

    private boolean isActuatorPath(String path){
        return path.equals("/actuator/health")||
                path.equals("/actuator/info");
    }

    private boolean isInternalPath(String path){
        return path.startsWith("/internal/policies/")||
                path.startsWith("/internal/products/");
    }

    private Mono<Void> forbidden(
            ServerWebExchange exchange,
            String message){

        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        String json="{"+
                "\"status\":403,"+
                "\"error\":\"Forbidden\","+
                "\"message\":\""+escape(message)+"\""+
                "}";

        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer=exchange.getResponse()
                .bufferFactory()
                .wrap(bytes);

        return exchange.getResponse()
                .writeWith(Mono.just(buffer));
    }

    private String escape(String value){
        return value.replace("\\","\\\\")
                .replace("\"","\\\"");
    }
}