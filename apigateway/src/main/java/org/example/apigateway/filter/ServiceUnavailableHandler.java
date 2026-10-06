package org.example.apigateway.filter;

import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.example.apigateway.dto.ErrorResponse;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * When a service is down (connection refused) or not registered in Eureka, answer
 * 503 Service Unavailable and name the service, instead of a bare 500. Every other
 * error is left to the default handler.
 */
@Slf4j
@Component
@Order(-2)
public class ServiceUnavailableHandler implements WebExceptionHandler{
    private static final Map<String,String> SERVICES=Map.of(
            "/api/auth","Auth service",
            "/api/users","Auth service",
            "/api/products","Product service",
            "/api/policies","Product service",
            "/api/claims","Claims service",
            "/api/triggers","Trigger service",
            "/api/notifications","Notification service",
            "/api/financial","Financial service"
    );

    private final ObjectMapper objectMapper;

    public ServiceUnavailableHandler(ObjectMapper objectMapper){
        this.objectMapper=objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange,Throwable error){
        if(!isServiceDown(error)||exchange.getResponse().isCommitted()){
            return Mono.error(error);
        }

        String path=exchange.getRequest().getPath().value();
        String service=serviceFor(path);
        log.warn("{} is not reachable for {} {}: {}",service,
                exchange.getRequest().getMethod(),path,rootMessage(error));

        ErrorResponse body=ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase())
                .message(service+" is not available right now. Make sure it is running and registered in Eureka.")
                .path(path)
                .build();

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer=exchange.getResponse().bufferFactory()
                .wrap(objectMapper.writeValueAsBytes(body));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /** Connection refused anywhere in the cause chain, or "no instance available" (503). */
    static boolean isServiceDown(Throwable error){
        for(Throwable t=error;t!=null;t=t.getCause()){
            if(t instanceof ConnectException){
                return true;
            }
            if(t instanceof ResponseStatusException status
                    &&status.getStatusCode().value()==HttpStatus.SERVICE_UNAVAILABLE.value()){
                return true;
            }
            if(t.getCause()==t){
                break;
            }
        }
        return false;
    }

    static String serviceFor(String path){
        return SERVICES.entrySet().stream()
                .filter(entry->path.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("The requested service");
    }

    private static String rootMessage(Throwable error){
        Throwable root=error;
        while(root.getCause()!=null&&root.getCause()!=root){
            root=root.getCause();
        }
        return root.getMessage();
    }
}
