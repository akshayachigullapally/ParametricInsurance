package org.example.financialservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
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
    void auditSendsEventSuccessfully(){
        when(client.sendAuditEvent(any(AuditEventRequest.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.audit(
                "QUOTE_CREATED","QUOTE",1L,5L,"productId=1"))
                .verifyComplete();

        verify(client).sendAuditEvent(any(AuditEventRequest.class));
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
    void notifyPaymentSuccessSendsNotification(){
        when(client.sendNotification(any(NotificationRequest.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.notifyPaymentSuccess(
                5L,"PAY-001",new BigDecimal("10000.00")))
                .verifyComplete();

        verify(client).sendNotification(any(NotificationRequest.class));
    }

    @Test
    void notifyPayoutProcessedSendsNotification(){
        when(client.sendNotification(any(NotificationRequest.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.notifyPayoutProcessed(
                5L,"PO-001",new BigDecimal("5000.00")))
                .verifyComplete();

        verify(client).sendNotification(any(NotificationRequest.class));
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

    @Test
    void notificationFailureIsTolerated(){
        when(client.sendNotification(any(NotificationRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("timeout")));

        StepVerifier.create(service.notifyPaymentSuccess(
                5L,"PAY-003",new BigDecimal("10000.00")))
                .verifyComplete();
    }

    @Test
    void auditWithNullActorId(){
        when(client.sendAuditEvent(any(AuditEventRequest.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.audit(
                "PAYMENT_SUCCESS","PAYMENT",1L,null,"paymentReference=PAY-001"))
                .verifyComplete();

        verify(client).sendAuditEvent(any(AuditEventRequest.class));
    }

    @Test
    void notifyPayoutFailedWithBlankReason(){
        when(client.sendNotification(any(NotificationRequest.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.notifyPayoutFailed(
                5L,"PO-003","  "))
                .verifyComplete();

        verify(client).sendNotification(any(NotificationRequest.class));
    }
}
