package org.example.claimsservice.service;

import org.example.claimsservice.util.GatewayHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialServiceClientTest{

    private FinancialServiceClient client(ExchangeFunction exchange){
        FinancialServiceClient client=new FinancialServiceClient(WebClient.builder().exchangeFunction(exchange));
        ReflectionTestUtils.setField(client,"internalKey","internal-key");
        return client;
    }

    @Test
    void createPayoutForClaim_shouldPostToInternalPayoutEndpoint(){
        AtomicReference<ClientRequest> captured=new AtomicReference<>();
        FinancialServiceClient client=client(request->{
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.CREATED)
                    .header("Content-Type","application/json")
                    .body("{\"id\":1}")
                    .build());
        });

        StepVerifier.create(client.createPayoutForClaim(10L)).verifyComplete();

        ClientRequest request=captured.get();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url().toString()).isEqualTo("http://FINANCIAL-SERVICE/internal/financial/payouts");
        assertThat(request.headers().getFirst(GatewayHeaders.GATEWAY_KEY)).isEqualTo("internal-key");
    }

    @Test
    void createPayoutForClaim_whenFinancialServiceRejects_shouldNotFailApproval(){
        FinancialServiceClient client=client(request->
                Mono.just(ClientResponse.create(HttpStatus.CONFLICT).build()));

        StepVerifier.create(client.createPayoutForClaim(10L)).verifyComplete();
    }

    @Test
    void createPayoutForClaim_whenFinancialServiceIsDown_shouldNotFailApproval(){
        FinancialServiceClient client=client(request->Mono.error(new IllegalStateException("connection refused")));

        StepVerifier.create(client.createPayoutForClaim(10L)).verifyComplete();
    }
}
