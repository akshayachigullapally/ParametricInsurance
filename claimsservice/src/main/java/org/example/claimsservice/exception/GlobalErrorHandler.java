package org.example.claimsservice.exception;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler{
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange,Throwable ex){
        HttpStatus status=resolveStatus(ex);
        logFailure(exchange,status,ex);
        List<String> messages=ex instanceof ValidationException validation
                ?validation.getErrors()
                :List.of(ex.getMessage()==null?"Unexpected error":ex.getMessage());

        ErrorResponse response=ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .messages(messages)
                .path(exchange.getRequest().getPath().value())
                .build();

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try{
            byte[] bytes=objectMapper.writeValueAsBytes(response);
            return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(bytes)
            ));
        }catch(Exception e){
            log.error("Failed to write error response body",e);
            return exchange.getResponse().setComplete();
        }
    }

    private void logFailure(ServerWebExchange exchange,HttpStatus status,Throwable ex){
        String method=String.valueOf(exchange.getRequest().getMethod());
        String path=exchange.getRequest().getPath().value();

        if(status.is5xxServerError()){
            log.error("{} {} failed with {}",method,path,status.value(),ex);
        }else{
            log.warn("{} {} rejected with {}: {}",method,path,status.value(),ex.getMessage());
        }
    }

    private HttpStatus resolveStatus(Throwable exception){
        if(exception instanceof NotFoundException)
            return HttpStatus.NOT_FOUND;

        if(exception instanceof ConflictException)
            return HttpStatus.CONFLICT;

        if(exception instanceof ForbiddenException)
            return HttpStatus.FORBIDDEN;

        if(exception instanceof ValidationException||
                exception instanceof IllegalArgumentException)
            return HttpStatus.BAD_REQUEST;

        if(exception instanceof DependentServiceException)
            return HttpStatus.SERVICE_UNAVAILABLE;

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}