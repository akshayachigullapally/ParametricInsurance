package org.example.financialservice.exception;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(-2)
public class GlobalErrorHandler implements ErrorWebExceptionHandler{
    private final ObjectMapper objectMapper;

    public GlobalErrorHandler(ObjectMapper objectMapper){
        this.objectMapper=objectMapper;
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            Throwable exception
    ){
        HttpStatus status=resolveStatus(exception);
        logFailure(exchange,status,exception);
        String message=resolveMessage(exception,status);

        ErrorResponse response=ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .timestamp(LocalDateTime.now())
                .build();

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse()
                .getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        try{
            byte[] bytes=objectMapper.writeValueAsBytes(response);

            return exchange.getResponse().writeWith(
                    Mono.just(
                            exchange.getResponse()
                                    .bufferFactory()
                                    .wrap(bytes)
                    )
            );
        }catch(Exception serializationException){
            log.error("Failed to write error response body",serializationException);
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
        if(exception instanceof NotFoundException){
            return HttpStatus.NOT_FOUND;
        }

        if(exception instanceof ConflictException||
                exception instanceof DuplicateKeyException||
                exception instanceof DataIntegrityViolationException){
            return HttpStatus.CONFLICT;
        }

        if(exception instanceof ForbiddenException){
            return HttpStatus.FORBIDDEN;
        }

        if(exception instanceof UnauthorizedException){
            return HttpStatus.UNAUTHORIZED;
        }

        if(exception instanceof ValidationException||
                exception instanceof WebExchangeBindException){
            return HttpStatus.BAD_REQUEST;
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveMessage(
            Throwable exception,
            HttpStatus status
    ){
        if(exception instanceof WebExchangeBindException bindException){
            return bindException.getFieldErrors()
                    .stream()
                    .map(error->
                            error.getField()+" "+error.getDefaultMessage()
                    )
                    .findFirst()
                    .orElse("Validation failed");
        }

        if(status==HttpStatus.INTERNAL_SERVER_ERROR){
            return "Unexpected server error";
        }

        return exception.getMessage();
    }
}