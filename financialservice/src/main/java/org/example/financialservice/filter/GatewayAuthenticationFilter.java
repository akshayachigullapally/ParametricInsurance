package org.example.financialservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.example.financialservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayAuthenticationFilter implements WebFilter{
    private final String internalKey;

    public GatewayAuthenticationFilter(
            @Value("${gateway.internal-key:cityshield-local-gateway-key}") String internalKey
    ){
        this.internalKey=internalKey;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain
    ){
        String path=exchange.getRequest().getPath().value();

        if(path.equals("/actuator")||
                path.startsWith("/actuator/")){
            return chain.filter(exchange);
        }

        String gatewayKey=exchange.getRequest()
                .getHeaders()
                .getFirst(GatewayHeaders.GATEWAY_KEY);

        if(!internalKey.equals(gatewayKey)){
            log.warn("Blocked request without valid gateway key: {} {}",
                    exchange.getRequest().getMethod(),path);
            return reject(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Invalid gateway key"
            );
        }

        if(path.startsWith("/internal/financial/")){
            return chain.filter(exchange);
        }

        String userId=exchange.getRequest()
                .getHeaders()
                .getFirst(GatewayHeaders.USER_ID);

        String username=exchange.getRequest()
                .getHeaders()
                .getFirst(GatewayHeaders.USER_EMAIL);

        String role=exchange.getRequest()
                .getHeaders()
                .getFirst(GatewayHeaders.USER_ROLE);

        if(userId==null||userId.isBlank()||
                username==null||username.isBlank()||
                role==null||role.isBlank()){
            return reject(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Missing trusted identity headers"
            );
        }

        try{
            Long.valueOf(userId);

            UsernamePasswordAuthenticationToken authentication=
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority(role))
                    );

            String customerId=exchange.getRequest()
                    .getHeaders()
                    .getFirst(GatewayHeaders.CUSTOMER_ID);

            if(customerId!=null&&!customerId.isBlank()){
                authentication.setDetails(
                        Long.valueOf(customerId)
                );
            }

            return chain.filter(exchange)
                    .contextWrite(
                            ReactiveSecurityContextHolder
                                    .withAuthentication(authentication)
                    );
        }catch(NumberFormatException exception){
            return reject(
                    exchange,
                    HttpStatus.BAD_REQUEST,
                    "Invalid trusted identity header"
            );
        }
    }

    private Mono<Void> reject(
            ServerWebExchange exchange,
            HttpStatus status,
            String message
    ){
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse()
                .getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse()
                .getHeaders()
                .setCacheControl("no-store");

        String body="{\"status\":"+status.value()+
                ",\"error\":\""+status.getReasonPhrase()+
                "\",\"message\":\""+message+"\"}";

        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);

        return exchange.getResponse().writeWith(
                Mono.just(
                        exchange.getResponse()
                                .bufferFactory()
                                .wrap(bytes)
                )
        );
    }
}