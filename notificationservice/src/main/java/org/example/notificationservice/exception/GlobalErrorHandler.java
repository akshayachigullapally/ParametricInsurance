package org.example.notificationservice.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component@Order(-2)
public class GlobalErrorHandler implements ErrorWebExceptionHandler{
private final ObjectMapper mapper;
public GlobalErrorHandler(ObjectMapper mapper){this.mapper=mapper;}
@Override public Mono<Void> handle(ServerWebExchange exchange,Throwable ex){
HttpStatus status=ex instanceof NotFoundException ?HttpStatus.NOT_FOUND:ex instanceof ForbiddenException ?HttpStatus.FORBIDDEN:ex instanceof UnauthorizedException ?HttpStatus.UNAUTHORIZED:ex instanceof ValidationException ||ex instanceof WebExchangeBindException?HttpStatus.BAD_REQUEST:ex instanceof ResponseStatusException rse ? (HttpStatus) rse.getStatusCode():HttpStatus.INTERNAL_SERVER_ERROR;
String message=ex instanceof WebExchangeBindException b?b.getFieldErrors().stream().map(e->e.getField()+" "+e.getDefaultMessage()).findFirst().orElse("Validation failed"):ex instanceof ResponseStatusException rse2 ? (rse2.getReason()!=null?rse2.getReason():status.getReasonPhrase()):ex.getMessage();
if(status==HttpStatus.INTERNAL_SERVER_ERROR)message="Unexpected server error";
ErrorResponse body= ErrorResponse.builder().status(status.value()).error(status.getReasonPhrase()).message(message).path(exchange.getRequest().getPath().value()).timestamp(LocalDateTime.now()).build();
exchange.getResponse().setStatusCode(status);exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
try{return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(mapper.writeValueAsBytes(body))));}catch(Exception e){return exchange.getResponse().setComplete();}
} }