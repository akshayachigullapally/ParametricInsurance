package org.example.productservice.client;

import org.example.productservice.dto.ProductTriggerSyncRequest;
import org.example.productservice.exception.DependentServiceException;
import org.example.productservice.util.GatewayHeaders;
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

import java.net.ConnectException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerServiceClientTest{

    static TriggerServiceClient client(ExchangeFunction exchange){
        TriggerServiceClient client=new TriggerServiceClient(
                WebClient.builder().baseUrl("http://TRIGGER-SERVICE").exchangeFunction(exchange).build());
        ReflectionTestUtils.setField(client,"internalKey","internal-key");
        return client;
    }

    static ExchangeFunction json(HttpStatus status,String body,List<ClientRequest> seen){
        return request->{
            seen.add(request);
            return Mono.just(ClientResponse.create(status).header("Content-Type","application/json").body(body).build());
        };
    }

    static ExchangeFunction unreachable(){
        return request->Mono.error(new WebClientRequestException(new ConnectException("refused"),
                HttpMethod.GET,URI.create("http://TRIGGER-SERVICE"),new HttpHeaders()));
    }

    private static ProductTriggerSyncRequest sync(){
        return ProductTriggerSyncRequest.builder().productId(4L).build();
    }

    @Test
    void readsZonesEligibilityAndMeasurementsWithTheInternalKey(){
        List<ClientRequest> seen=new ArrayList<>();
        TriggerServiceClient zones=client(json(HttpStatus.OK,"[{\"id\":1},{\"id\":2}]",seen));
        TriggerServiceClient single=client(json(HttpStatus.OK,"{\"id\":1,\"eligible\":true}",seen));

        StepVerifier.create(zones.getActiveZonesList()).assertNext(list->assertThat(list).hasSize(2)).verifyComplete();
        StepVerifier.create(zones.getCurrentMeasurements()).expectNextCount(2).verifyComplete();
        StepVerifier.create(single.getZoneById(1L)).expectNextCount(1).verifyComplete();
        StepVerifier.create(single.getPolicyEligibility(7L)).expectNextCount(1).verifyComplete();

        assertThat(seen).extracting(r->r.url().getPath()).containsExactly(
                "/internal/triggers/zones/active",
                "/internal/triggers/measurements/current",
                "/internal/triggers/zones/1",
                "/internal/triggers/policy/7/eligibility");
        assertThat(seen).allSatisfy(r->assertThat(r.headers().getFirst(GatewayHeaders.GATEWAY_KEY)).isEqualTo("internal-key"));
    }

    @Test
    void failuresBecomeClearDependentServiceErrors(){
        List<ClientRequest> seen=new ArrayList<>();
        TriggerServiceClient rejected=client(json(HttpStatus.BAD_REQUEST,"{}",seen));
        TriggerServiceClient broken=client(json(HttpStatus.INTERNAL_SERVER_ERROR,"{}",seen));
        TriggerServiceClient down=client(unreachable());

        StepVerifier.create(rejected.syncProductDefinition(sync()))
                .expectErrorMessage("Trigger service rejected the product threshold definition").verify();
        StepVerifier.create(broken.syncProductDefinition(sync()))
                .expectErrorMessage("Trigger service is currently unavailable").verify();
        StepVerifier.create(down.syncProductDefinition(sync())).expectError(DependentServiceException.class).verify();
        StepVerifier.create(down.getCurrentMeasurements()).expectError(DependentServiceException.class).verify();
        StepVerifier.create(down.getPolicyEligibility(7L)).expectError(DependentServiceException.class).verify();
        StepVerifier.create(down.syncProductDefinition(null))
                .expectErrorMessage("Trigger synchronization payload is incomplete").verify();
    }
}
