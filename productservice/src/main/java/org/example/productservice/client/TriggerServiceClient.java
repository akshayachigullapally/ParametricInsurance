package org.example.productservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.productservice.dto.ProductTriggerSyncRequest;
import org.example.productservice.dto.InternalZoneResponse;
import org.example.productservice.exception.DependentServiceException;
import org.example.productservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerServiceClient{
    @Qualifier("triggerServiceWebClient")
    private final WebClient triggerServiceWebClient;

    @Value("${gateway.internal-key}")
    private String internalKey;

    public Flux<InternalZoneResponse> getActiveZones(){
        return triggerServiceWebClient.get()
                .uri("/internal/triggers/zones/active")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(InternalZoneResponse.class);
    }

    public Mono<List<InternalZoneResponse>> getActiveZonesList(){
        return getActiveZones().collectList();
    }

        public Mono<org.example.productservice.dto.PolicyTriggerEligibilityResponse> getPolicyEligibility(Long policyId){
                return triggerServiceWebClient.get()
                                .uri("/internal/triggers/policy/{policyId}/eligibility", policyId)
                                .header(GatewayHeaders.GATEWAY_KEY, internalKey)
                                .accept(MediaType.APPLICATION_JSON)
                                .retrieve()
                                .bodyToMono(org.example.productservice.dto.PolicyTriggerEligibilityResponse.class)
                                .onErrorMap(WebClientRequestException.class, ex-> new org.example.productservice.exception.DependentServiceException("Trigger service is currently unavailable"));
        }

    public Mono<InternalZoneResponse> getZoneById(Long zoneId){
        return triggerServiceWebClient.get()
                .uri("/internal/triggers/zones/{zoneId}",zoneId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(InternalZoneResponse.class);
    }

    public Mono<Void> syncProductDefinition(ProductTriggerSyncRequest request){
        if(request==null||request.getProductId()==null){
            return Mono.error(new DependentServiceException(
                    "Trigger synchronization payload is incomplete"
            ));
        }

        return triggerServiceWebClient.put()
                .uri("/internal/triggers/products/{productId}/definition",
                        request.getProductId())
                .header(GatewayHeaders.GATEWAY_KEY,internalKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError,response->
                        Mono.error(new DependentServiceException(
                                "Trigger service is currently unavailable"
                        )))
                .onStatus(
                        status->status.is4xxClientError(),
                        response->Mono.error(new DependentServiceException(
                                "Trigger service rejected the product threshold definition"
                        ))
                )
                .bodyToMono(Void.class)
                .onErrorMap(
                        WebClientRequestException.class,
                        exception->new DependentServiceException(
                                "Trigger service is currently unavailable"
                        )
                );
    }

    public Mono<Void> evaluateProductWithOverride(Long productId, ProductTriggerSyncRequest request){
        if(productId==null||request==null){
            return Mono.error(new org.example.productservice.exception.DependentServiceException(
                    "Trigger evaluation payload is incomplete"
            ));
        }

        return triggerServiceWebClient.post()
                .uri("/internal/triggers/products/{productId}/evaluate", productId)
                .header(GatewayHeaders.GATEWAY_KEY, internalKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new org.example.productservice.exception.DependentServiceException(
                                "Trigger service is currently unavailable"
                        ))
                )
                .onStatus(status -> status.is4xxClientError(), response -> Mono.error(new org.example.productservice.exception.DependentServiceException(
                        "Trigger service rejected the product evaluation request"
                )))
                .bodyToMono(Void.class)
                .onErrorMap(WebClientRequestException.class, exception -> new org.example.productservice.exception.DependentServiceException(
                        "Trigger service is currently unavailable"
                ));
    }
}