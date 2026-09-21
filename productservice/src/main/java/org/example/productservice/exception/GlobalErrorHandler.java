package org.example.productservice.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
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

    private HttpStatus resolveStatus(Throwable ex){
        if(ex instanceof ValidationException) return HttpStatus.BAD_REQUEST;
        if(ex instanceof NotFoundException) return HttpStatus.NOT_FOUND;
        if(ex instanceof ConflictException||ex instanceof DuplicateKeyException) return HttpStatus.CONFLICT;
        if(ex instanceof ForbiddenException) return HttpStatus.FORBIDDEN;
        if(ex instanceof DependentServiceException) return HttpStatus.SERVICE_UNAVAILABLE;
        if(ex instanceof ServerWebInputException) return HttpStatus.BAD_REQUEST;
        if(ex instanceof UnsupportedMediaTypeStatusException) return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        if(ex instanceof DataAccessException) return HttpStatus.INTERNAL_SERVER_ERROR;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}