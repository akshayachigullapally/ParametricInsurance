package org.example.productservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.productservice.dto.InternalQuoteConsumeRequest;
import org.example.productservice.dto.InternalQuoteResponse;
import org.example.productservice.dto.InternalQuoteValidateRequest;
import org.example.productservice.exception.DependentServiceException;
import org.example.productservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialServiceClient{
    @org.springframework.beans.factory.annotation.Qualifier("financialServiceWebClient")
    private final WebClient financialServiceWebClient;

    @Value("${gateway.internal-key:cityshield-local-gateway-key}")
    private String internalKey;

    public Mono<InternalQuoteResponse> validateQuote(
            Long quoteId,
            InternalQuoteValidateRequest request
    ){
        log.debug("Validate quote [financial service]: quoteId={}",quoteId);
        return financialServiceWebClient.post()
                .uri("/internal/financial/quotes/{quoteId}/validate",quoteId)
                .header(GatewayHeaders.GATEWAY_KEY,internalKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status->status.value()==404,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Quote could not be found"
                                )
                        )
                )
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Quote could not be validated"
                                )
                        )
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Financial service is currently unavailable"
                                )
                        )
                )
                .bodyToMono(InternalQuoteResponse.class)
                .onErrorMap(
                        WebClientRequestException.class,
                        exception->new DependentServiceException(
                                "Financial service is currently unavailable"
                        )
                );
    }

    public Mono<InternalQuoteResponse> consumeQuote(
            Long quoteId,
            InternalQuoteConsumeRequest request
    ){
        return financialServiceWebClient.put()
                .uri("/internal/financial/quotes/{quoteId}/consume",quoteId)
                .header(GatewayHeaders.GATEWAY_KEY,internalKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Quote could not be linked to the policy"
                                )
                        )
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Financial service is currently unavailable"
                                )
                        )
                )
                .bodyToMono(InternalQuoteResponse.class)
                .onErrorMap(
                        WebClientRequestException.class,
                        exception->new DependentServiceException(
                                "Financial service is currently unavailable"
                        )
                )
                .doOnError(error->log.warn(
                        "Failed to mark quote {} consumed for policy: {}",
                        quoteId,error.getMessage()
                ));
    }

    /**
     * Premium from the active pricing rule. Empty when pricing is unavailable,
     * so callers can fall back instead of failing the application.
     */
    public Mono<java.math.BigDecimal> calculatePremium(
            Long productId,
            Long locationZoneId,
            java.math.BigDecimal sumInsured,
            Integer termMonths
    ){
        return financialServiceWebClient.post()
                .uri("/internal/financial/pricing/calculate")
                .header(GatewayHeaders.GATEWAY_KEY,internalKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of(
                        "productId",productId,
                        "locationZoneId",locationZoneId,
                        "sumInsured",sumInsured,
                        "termMonths",termMonths))
                .retrieve()
                .bodyToMono(org.example.productservice.dto.InternalPricingResponse.class)
                .mapNotNull(org.example.productservice.dto.InternalPricingResponse::getPremiumAmount)
                .onErrorResume(error->{
                    log.warn("Pricing unavailable for product {} zone {}: {}",
                            productId,locationZoneId,error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Whether the policy's latest premium payment succeeded. Any failure to
     * reach the financial service counts as unpaid, so an unverified policy
     * is never paid out.
     */
    public Mono<Boolean> isPremiumPaid(Long policyId){
        return financialServiceWebClient.get()
                .uri("/internal/financial/payments/policy/{policyId}/status",policyId)
                .header(GatewayHeaders.GATEWAY_KEY,internalKey)
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .map(body->Boolean.TRUE.equals(body.get("successful")))
                .defaultIfEmpty(false)
                .onErrorResume(error->{
                    log.warn("Payment status unavailable for policy {}: {}",
                            policyId,error.getMessage());
                    return Mono.just(false);
                });
    }
}
