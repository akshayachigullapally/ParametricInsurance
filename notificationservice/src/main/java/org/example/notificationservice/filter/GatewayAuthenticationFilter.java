package org.example.notificationservice.filter;

import org.example.notificationservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayAuthenticationFilter implements WebFilter{
private final String internalKey;
public GatewayAuthenticationFilter(@Value("${gateway.internal-key}")String internalKey){this.internalKey=internalKey;}
@Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
String path=exchange.getRequest().getPath().value();
if(path.equals("/actuator/health")||path.equals("/actuator/info"))return chain.filter(exchange);
String key=exchange.getRequest().getHeaders().getFirst(GatewayHeaders.GATEWAY_KEY);
if(!internalKey.equals(key))return reject(exchange,HttpStatus.UNAUTHORIZED,"Invalid gateway key");
if(path.startsWith("/internal/"))return chain.filter(exchange);
String userId=exchange.getRequest().getHeaders().getFirst(GatewayHeaders.USER_ID);
String role=exchange.getRequest().getHeaders().getFirst(GatewayHeaders.USER_ROLE);
if(userId==null||role==null)return reject(exchange,HttpStatus.UNAUTHORIZED,"Missing trusted identity headers");
try{
UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(userId,null,List.of(new SimpleGrantedAuthority(role)));
String customerId=exchange.getRequest().getHeaders().getFirst(GatewayHeaders.CUSTOMER_ID);
if(customerId!=null&&!customerId.isBlank())auth.setDetails(Long.valueOf(customerId));
return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
}catch(NumberFormatException e){return reject(exchange,HttpStatus.BAD_REQUEST,"Invalid trusted identity header");}
}
private Mono<Void> reject(ServerWebExchange e,HttpStatus s,String m){e.getResponse().setStatusCode(s);e.getResponse().getHeaders().set("Content-Type","application/json");byte[] b=("{\"status\":"+s.value()+",\"message\":\""+m+"\"}").getBytes();return e.getResponse().writeWith(Mono.just(e.getResponse().bufferFactory().wrap(b)));}
}