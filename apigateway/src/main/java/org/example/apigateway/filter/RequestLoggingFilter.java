package org.example.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Access log for every request that passes through the gateway:
 * method, path, final status and time taken.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements WebFilter{

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        String method=String.valueOf(exchange.getRequest().getMethod());
        String path=exchange.getRequest().getPath().value();

        if(path.startsWith("/actuator")){
            return chain.filter(exchange);
        }

        long start=System.currentTimeMillis();
        log.trace("Incoming {} {}",method,path);

        return chain.filter(exchange)
                .doFinally(signal->{
                    HttpStatusCode status=exchange.getResponse().getStatusCode();
                    long took=System.currentTimeMillis()-start;
                    int code=status==null?0:status.value();

                    if(code>=500){
                        log.error("{} {} -> {} ({} ms)",method,path,code,took);
                    }else if(code>=400){
                        log.warn("{} {} -> {} ({} ms)",method,path,code,took);
                    }else{
                        log.info("{} {} -> {} ({} ms)",method,path,code,took);
                    }
                });
    }
}
