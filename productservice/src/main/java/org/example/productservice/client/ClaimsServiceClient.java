package org.example.productservice.client;

import lombok.extern.slf4j.Slf4j;
import org.example.productservice.dto.InternalClaimResponse;
import org.example.productservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ClaimsServiceClient{
    private final WebClient webClient;

    public ClaimsServiceClient(
            WebClient.Builder builder,
            @Value("${gateway.internal-key:cityshield-local-gateway-key}") String internalKey
    ){
        this.webClient=builder
                .baseUrl("http://CLAIMS-SERVICE")
                .defaultHeader(GatewayHeaders.GATEWAY_KEY,internalKey)
                .build();
    }

    public Mono<InternalClaimResponse> createInternalClaim(Object payload){
        log.debug("Create internal claim [claims service]");
        return webClient.post()
                .uri("/internal/claims")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        response->Mono.error(new RuntimeException("Claims service rejected internal create")))
                .bodyToMono(InternalClaimResponse.class)
                .onErrorMap(WebClientRequestException.class, ex->new RuntimeException("Claims service is unavailable"));
    }
}
