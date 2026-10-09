package org.example.financialservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.financialservice.dto.InternalPolicyResponse;
import org.example.financialservice.dto.InternalProductResponse;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ProductServiceClient{
    private final WebClient webClient;

    public ProductServiceClient(
            WebClient.Builder builder,
            @Value("${gateway.internal-key:climatesure-local-gateway-key}") String internalKey
    ){
        this.webClient=builder
                .baseUrl("http://PRODUCT-SERVICE")
                .defaultHeader(
                        GatewayHeaders.GATEWAY_KEY,
                        internalKey
                )
                .build();
    }

    public Mono<InternalProductResponse> findProduct(Long productId){
        log.debug("Find product: productId={}",productId);
        return webClient.get()
                .uri("/internal/products/{productId}",productId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        response->Mono.error(
                                new NotFoundException(
                                        "Product "+productId+" not found"
                                )
                        )
                )
                .bodyToMono(InternalProductResponse.class);
    }

    public Flux<InternalProductResponse> findAllProducts(){
        log.debug("Find all products");
        return webClient.get()
                .uri("/internal/products")
                .retrieve()
                .bodyToFlux(InternalProductResponse.class);
    }

    public Mono<Void> requireActiveProduct(Long productId){
        log.debug("Require active product: productId={}",productId);
        return findProduct(productId)
                .flatMap(product->"ACTIVE".equals(
                        product.getStatus()
                )
                        ?Mono.empty()
                        :Mono.error(new ConflictException(
                        "Product is not active"
                ))
                );
    }

    public Mono<InternalPolicyResponse> findPolicy(Long policyId){
        log.debug("Find policy [product service]: policyId={}",policyId);
        return webClient.get()
                .uri("/internal/policies/{policyId}",policyId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        response->Mono.error(
                                new NotFoundException(
                                        "Policy "+policyId+" not found"
                                )
                        )
                )
                .bodyToMono(InternalPolicyResponse.class);
    }
}