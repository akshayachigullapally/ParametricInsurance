package org.example.productservice.service;

import org.example.productservice.dto.AuditEventRequest;
import org.example.productservice.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceClientTest{

    @Test
    void retriesTemporaryOutagesAndSendsToTheInternalEndpoints(){
        List<String> paths=new ArrayList<>();
        AtomicInteger calls=new AtomicInteger();
        NotificationServiceClient client=new NotificationServiceClient(WebClient.builder().exchangeFunction(request->{
            paths.add(request.url().getPath());
            return Mono.just(ClientResponse.create(calls.incrementAndGet()==1?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.CREATED).build());
        }),"internal-key");

        StepVerifier.create(client.sendNotification(NotificationRequest.builder().type("POLICY_APPROVED").build())).verifyComplete();
        StepVerifier.create(client.sendAuditEvent(AuditEventRequest.builder().eventType("POLICY_APPROVED").build())).verifyComplete();

        assertThat(paths).containsExactly("/internal/notifications","/internal/notifications","/internal/audit-events");
    }
}
