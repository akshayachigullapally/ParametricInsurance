package org.example.authservice.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler{
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange,Throwable ex){
        HttpStatus status=resolveStatus(ex);
        List<String> messages=ex instanceof ValidationException ve
                ?ve.getErrors()
                :List.of(ex.getMessage()==null?"Unexpected error":ex.getMessage());

        ErrorResponse error=new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                messages,
                exchange.getRequest().getPath().value()
        );

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try{
            byte[] data=objectMapper.writeValueAsBytes(error);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(data))
            );
        }catch(Exception e){
            return exchange.getResponse().setComplete();
        }
    }

    private HttpStatus resolveStatus(Throwable ex){
        if(ex instanceof ConflictException) return HttpStatus.CONFLICT;
        if(ex instanceof AuthenticationException) return HttpStatus.UNAUTHORIZED;
        if(ex instanceof NotFoundException) return HttpStatus.NOT_FOUND;
        if(ex instanceof ValidationException) return HttpStatus.BAD_REQUEST;
        if(ex instanceof ForbiddenException) return HttpStatus.FORBIDDEN;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}