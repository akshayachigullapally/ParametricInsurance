package org.example.financialservice.handler;

import org.example.financialservice.service.MetricsService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class MetricsHandler{
    private final MetricsService service;

    public MetricsHandler(MetricsService service){
        this.service=service;
    }

    public Mono<ServerResponse> metrics(ServerRequest request){
        return service.metrics()
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }
}