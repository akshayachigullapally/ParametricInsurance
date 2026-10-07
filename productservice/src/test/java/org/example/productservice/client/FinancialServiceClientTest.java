package org.example.productservice.client;

import org.example.productservice.dto.InternalQuoteConsumeRequest;
import org.example.productservice.dto.InternalQuoteValidateRequest;
import org.example.productservice.exception.DependentServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialServiceClientTest{

    private static FinancialServiceClient client(HttpStatus status,String body,List<ClientRequest> seen){
        return client(request->{
            seen.add(request);
            return Mono.just(ClientResponse.create(status).header("Content-Type","application/json").body(body).build());
        });
    }

    private static FinancialServiceClient client(ExchangeFunction exchange){
        FinancialServiceClient client=new FinancialServiceClient(
                WebClient.builder().baseUrl("http://FINANCIAL-SERVICE").exchangeFunction(exchange).build());
        ReflectionTestUtils.setField(client,"internalKey","internal-key");
        return client;
    }

    private static FinancialServiceClient down(){
        return client(request->Mono.error(new WebClientRequestException(new ConnectException("refused"),
                HttpMethod.POST,URI.create("http://FINANCIAL-SERVICE"),new HttpHeaders())));
    }

    private static final InternalQuoteValidateRequest VALIDATE=InternalQuoteValidateRequest.builder().customerId(3L).productId(1L).build();
    private static final InternalQuoteConsumeRequest CONSUME=InternalQuoteConsumeRequest.builder().policyId(9L).policyNumber("POL-1").build();

    @Test
    void quoteErrorsAreTranslated(){
        List<ClientRequest> seen=new ArrayList<>();

        StepVerifier.create(client(HttpStatus.NOT_FOUND,"{}",seen).validateQuote(5L,VALIDATE))
                .expectErrorMessage("Quote could not be found").verify();
        StepVerifier.create(client(HttpStatus.CONFLICT,"{}",seen).validateQuote(5L,VALIDATE))
                .expectErrorMessage("Quote could not be validated").verify();
        StepVerifier.create(client(HttpStatus.BAD_GATEWAY,"{}",seen).validateQuote(5L,VALIDATE))
                .expectErrorMessage("Financial service is currently unavailable").verify();
        StepVerifier.create(client(HttpStatus.CONFLICT,"{}",seen).consumeQuote(5L,CONSUME))
                .expectErrorMessage("Quote could not be linked to the policy").verify();
        StepVerifier.create(client(HttpStatus.BAD_GATEWAY,"{}",seen).consumeQuote(5L,CONSUME))
                .expectErrorMessage("Financial service is currently unavailable").verify();
        StepVerifier.create(down().validateQuote(5L,VALIDATE)).expectError(DependentServiceException.class).verify();
        StepVerifier.create(down().consumeQuote(5L,CONSUME)).expectError(DependentServiceException.class).verify();
    }

    @Test
    void pricingAndPaymentStatusFallBackSafelyWhenUnavailable(){
        List<ClientRequest> seen=new ArrayList<>();

        StepVerifier.create(client(HttpStatus.OK,"{\"premiumAmount\":1250.50}",seen)
                        .calculatePremium(1L,2L,new BigDecimal("50000"),12))
                .expectNext(new BigDecimal("1250.50")).verifyComplete();
        StepVerifier.create(down().calculatePremium(1L,2L,BigDecimal.TEN,12)).verifyComplete();

        StepVerifier.create(client(HttpStatus.OK,"{\"successful\":true}",seen).isPremiumPaid(9L,"POL-9"))
                .expectNext(true).verifyComplete();
        StepVerifier.create(client(HttpStatus.OK,"{\"successful\":false}",seen).isPremiumPaid(9L,null))
                .expectNext(false).verifyComplete();
        StepVerifier.create(down().isPremiumPaid(9L,"POL-9")).expectNext(false).verifyComplete();

        assertThat(seen.get(1).url().toString()).endsWith("/internal/financial/payments/policy/9/status?policyNumber=POL-9");
        assertThat(seen.get(2).url().getQuery()).isNull();
    }
}
