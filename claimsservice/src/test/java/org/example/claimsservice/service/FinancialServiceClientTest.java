package org.example.claimsservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;



class FinancialServiceClientTest{

    private FinancialServiceClient client(ExchangeFunction exchange){
        FinancialServiceClient client=new FinancialServiceClient(WebClient.builder().exchangeFunction(exchange));
        ReflectionTestUtils.setField(client,"internalKey","internal-key");
        return client;
    }

    @Test
    void createPayoutForClaim_whenFinancialServiceRejects_shouldNotFailApproval(){
        FinancialServiceClient client=client(request->
                Mono.just(ClientResponse.create(HttpStatus.CONFLICT).build()));

        StepVerifier.create(client.createPayoutForClaim(10L)).verifyComplete();
    }
}
