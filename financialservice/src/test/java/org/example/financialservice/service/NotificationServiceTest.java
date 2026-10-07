package org.example.financialservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.example.financialservice.dto.AuditEventRequest;
import org.example.financialservice.dto.NotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class NotificationServiceTest{
    private NotificationServiceClient client;
    private NotificationService service;

    @BeforeEach
    void setUp(){
        client=org.mockito.Mockito.mock(NotificationServiceClient.class);
        service=new NotificationService(client);
    }

    @Test
    void auditToleratesFailure(){
        when(client.sendAuditEvent(any(AuditEventRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("timeout")));

        StepVerifier.create(service.audit(
                "QUOTE_CREATED","QUOTE",1L,5L,"productId=1"))
                .verifyComplete();
    }

    @Test
    void notifyPayoutFailedSendsNotification(){
        when(client.sendNotification(any(NotificationRequest.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.notifyPayoutFailed(
                5L,"PO-002","Bank decline"))
                .verifyComplete();

        verify(client).sendNotification(any(NotificationRequest.class));
    }
}
