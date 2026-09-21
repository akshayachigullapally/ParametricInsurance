package org.example.triggerservice.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

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
            return exchange.getResponse().setComplete();
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