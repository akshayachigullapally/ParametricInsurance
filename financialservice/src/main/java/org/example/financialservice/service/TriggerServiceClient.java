package org.example.financialservice.service;

import org.example.financialservice.dto.ZoneDetail;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TriggerServiceClient{
    private final WebClient webClient;

    public TriggerServiceClient(
            WebClient.Builder builder,
            @Value("${gateway.internal-key}") String internalKey
    ){
        this.webClient=builder
                .baseUrl("http://TRIGGER-SERVICE")
                .defaultHeader(
                        GatewayHeaders.GATEWAY_KEY,
                        internalKey
                )
                .build();
    }

    public Mono<ZoneDetail> findZone(Long zoneId){
        return webClient.get()
                .uri("/internal/triggers/zones/{zoneId}",zoneId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        response->Mono.error(
                                new NotFoundException(
                                        "Zone "+zoneId+" not found"
                                )
                        )
                )
                .bodyToMono(ZoneDetail.class);
    }

    public Mono<Void> requireActiveZone(Long zoneId){
        return findZone(zoneId)
                .flatMap(zone->"ACTIVE".equals(zone.getStatus())
                        ?Mono.empty()
                        :Mono.error(new ConflictException(
                        "Zone is not active"
                ))
                );
    }

    public Mono<Void> markEventProcessed(Long eventId){
        return webClient.put()
                .uri(
                        "/internal/triggers/events/{eventId}/processed",
                        eventId
                )
                .retrieve()
                .toBodilessEntity()
                .then();
    }
}