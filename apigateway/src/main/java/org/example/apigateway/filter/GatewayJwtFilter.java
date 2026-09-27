package org.example.apigateway.filter;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.example.apigateway.dto.ErrorResponse;
import org.example.apigateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Component
public class GatewayJwtFilter implements WebFilter{
    public static final String GATEWAY_KEY_HEADER="X-Gateway-Key";
    public static final String USER_ID_HEADER="X-Authenticated-User-Id";
    public static final String USER_HEADER="X-Authenticated-User";
    public static final String ROLE_HEADER="X-Authenticated-Role";
    public static final String CUSTOMER_ID_HEADER="X-Authenticated-Customer-Id";

    private static final Set<String> ALLOWED_ROLES=Set.of(
            "ROLE_CUSTOMER",
            "ROLE_UNDERWRITER",
            "ROLE_CLAIMS_OFFICER",
            "ROLE_CLAIMS_REVIEWER",
            "ROLE_ADMIN"
    );

    private static String downstreamRole(String role){
        if("ROLE_CLAIMS_REVIEWER".equals(role)){
            return "ROLE_CLAIMS_OFFICER";
        }

        return role;
    }

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final String internalGatewayKey;

    public GatewayJwtFilter(JwtUtil jwtUtil,ObjectMapper objectMapper,
                            @Value("${gateway.internal-key}") String internalGatewayKey){
        this.jwtUtil=jwtUtil;
        this.objectMapper=objectMapper;
        this.internalGatewayKey=internalGatewayKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        ServerHttpRequest sanitizedRequest=sanitizeHeaders(exchange.getRequest());
        ServerWebExchange sanitizedExchange=exchange.mutate()
                .request(sanitizedRequest)
                .build();

        if(isPublicRequest(sanitizedRequest)){
            return chain.filter(addGatewayKey(sanitizedExchange));
        }

        boolean webSocket=
                sanitizedRequest.getPath().value().startsWith("/ws/");
        boolean sseStream=
                sanitizedRequest.getPath().value().equals("/api/notifications/me/stream");

        String authorization=sanitizedRequest.getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        String token;
        if(authorization!=null&&authorization.startsWith("Bearer ")){
            token=authorization.substring(7).trim();
        }else if(webSocket||sseStream){
            token=queryParam(
                    sanitizedRequest.getURI().getQuery(),
                    sseStream?"access_token":"token"
            );
        }else{
            token=null;
        }

        if(token==null||token.isBlank()){
            return unauthorized(
                    sanitizedExchange,
                    "Authorization token is required"
            );
        }

        try{
            Claims claims=jwtUtil.parseToken(token);

            if(jwtUtil.isExpired(claims)){
                return unauthorized(
                        sanitizedExchange,
                        "JWT token is expired"
                );
            }

            Long userId=jwtUtil.extractUserId(claims);
            String email=jwtUtil.extractEmail(claims);
            String role=jwtUtil.extractRole(claims);
            Long customerId=jwtUtil.extractCustomerId(claims);

            if(email==null||email.isBlank()||
                    role==null||role.isBlank()){
                return unauthorized(
                        sanitizedExchange,
                        "JWT does not contain required claims"
                );
            }

            if(!ALLOWED_ROLES.contains(role)){
                return unauthorized(
                        sanitizedExchange,
                        "JWT contains invalid role"
                );
            }

            ServerHttpRequest securedRequest=sanitizedRequest.mutate()
                    .headers(headers->{
                        headers.set(GATEWAY_KEY_HEADER,internalGatewayKey);
                        headers.set(USER_ID_HEADER,userId.toString());
                        headers.set(USER_HEADER,email);
                        headers.set(ROLE_HEADER,downstreamRole(role));

                        if(customerId!=null){
                            headers.set(
                                    CUSTOMER_ID_HEADER,
                                    customerId.toString()
                            );
                        }
                    })
                    .build();

            ServerWebExchange securedExchange=sanitizedExchange.mutate()
                    .request(securedRequest)
                    .build();

            log.debug("Authenticated userId={} role={} for {} {}",
                    userId,role,sanitizedRequest.getMethod(),
                    sanitizedRequest.getPath().value());

            return chain.filter(securedExchange);
        }catch(IllegalArgumentException e){
            return unauthorized(
                    sanitizedExchange,
                    "Invalid or expired JWT token"
            );
        }
    }

    private ServerHttpRequest sanitizeHeaders(ServerHttpRequest request){
        return request.mutate()
                .headers(headers->{
                    headers.remove(GATEWAY_KEY_HEADER);
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_HEADER);
                    headers.remove(ROLE_HEADER);
                    headers.remove(CUSTOMER_ID_HEADER);
                })
                .build();
    }

    private String queryParam(String query,String key){
        if(query==null||query.isBlank()) return null;

        for(String part:query.split("&")){
            String[] kv=part.split("=",2);
            if(kv.length==2&&kv[0].equals(key)) return kv[1];
        }

        return null;
    }

    private ServerWebExchange addGatewayKey(ServerWebExchange exchange){
        ServerHttpRequest request=exchange.getRequest().mutate()
                .headers(headers->headers.set(
                        GATEWAY_KEY_HEADER,
                        internalGatewayKey
                ))
                .build();

        return exchange.mutate().request(request).build();
    }

    private boolean isPublicRequest(ServerHttpRequest request){
        String path=request.getPath().value();
        HttpMethod method=request.getMethod();

        if(HttpMethod.OPTIONS.equals(method)) return true;

        // Gateway's own actuator (/actuator/**) and each service's
        // actuator proxied through the gateway (/{service}/actuator/**).
        if(path.matches("^(/[a-z-]+)?/actuator(/.*)?$")){
            return true;
        }

        if(HttpMethod.POST.equals(method)){
            return path.equals("/api/auth/register")||
                    path.equals("/api/auth/login")||
                    path.equals("/api/auth/refresh");
        }

        if(HttpMethod.GET.equals(method)){
            if(path.equals("/api/products")) return true;
            if(path.matches("^/api/products/[0-9]+$")) return true;

            return path.matches(
                    "^/api/products/category/[A-Za-z0-9_-]+$"
            );
        }

        return false;
    }

    private Mono<Void> unauthorized(
            ServerWebExchange exchange,
            String message){

        if(exchange.getResponse().isCommitted()) return Mono.empty();

        log.warn("Rejected {} {}: {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                message);

        ErrorResponse error=ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .build();

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        try{
            byte[] bytes=objectMapper.writeValueAsBytes(error);
            DataBuffer buffer=exchange.getResponse()
                    .bufferFactory()
                    .wrap(bytes);

            return exchange.getResponse()
                    .writeWith(Mono.just(buffer));
        }catch(Exception e){
            log.error("Failed to write unauthorized response body",e);
            return exchange.getResponse().setComplete();
        }
    }
}