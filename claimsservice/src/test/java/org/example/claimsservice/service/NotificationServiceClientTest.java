package org.example.claimsservice.service;

import org.example.claimsservice.dto.AuditEventRequest;
import org.example.claimsservice.dto.NotificationRequest;
import org.example.claimsservice.util.GatewayHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceClientTest{

    private static NotificationServiceClient client(ExchangeFunction exchange){
        return new NotificationServiceClient(WebClient.builder().exchangeFunction(exchange),"internal-key");
    }

    @Test
    void sendsNotificationsAndAuditEventsWithTheInternalKey(){
        List<ClientRequest> requests=new ArrayList<>();
        NotificationServiceClient client=client(request->{
            requests.add(request);
            return Mono.just(ClientResponse.create(HttpStatus.CREATED).build());
        });

        StepVerifier.create(client.sendNotification(NotificationRequest.builder().type("CLAIM_SUBMITTED").build()))
                .verifyComplete();
        StepVerifier.create(client.sendAuditEvent(AuditEventRequest.builder().eventType("CLAIM_APPROVED").build()))
                .verifyComplete();

        assertThat(requests).extracting(r->r.url().toString()).containsExactly(
                "http://NOTIFICATION-SERVICE/internal/notifications",
                "http://NOTIFICATION-SERVICE/internal/audit-events");
        assertThat(requests).allSatisfy(r->
                assertThat(r.headers().getFirst(GatewayHeaders.GATEWAY_KEY)).isEqualTo("internal-key"));
    }

    @Test
    void retriesWhileTheNotificationServiceIsTemporarilyUnavailable(){
        AtomicInteger calls=new AtomicInteger();
        NotificationServiceClient client=client(request->Mono.just(ClientResponse.create(
                calls.incrementAndGet()<3?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.CREATED).build()));

        StepVerifier.create(client.sendNotification(NotificationRequest.builder().build())).verifyComplete();

        assertThat(calls).hasValue(3);
    }
}
