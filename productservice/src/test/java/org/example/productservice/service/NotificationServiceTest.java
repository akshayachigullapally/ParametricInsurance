package org.example.productservice.service;

import org.example.productservice.dto.AuditEventRequest;
import org.example.productservice.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest{

    @Mock
    private NotificationServiceClient client;

    @InjectMocks
    private NotificationService service;

    @Test
    void everyPolicyAndProductEventSendsTheRightNotification(){
        when(client.sendNotification(any())).thenReturn(Mono.empty());

        Mono.when(
                service.notifyPolicySubmitted(3L,1L,"POL-1"),
                service.notifyPolicyPendingReview(1L,"POL-1"),
                service.notifyPolicyApproved(3L,1L,"POL-1"),
                service.notifyPolicyRejected(3L,1L,"POL-1","Outside cover area"),
                service.notifyPolicyRejected(3L,1L,"POL-1"," "),
                service.notifyPolicyCancelled(3L,1L,"POL-1"),
                service.notifyPolicyExpired(3L,1L,"POL-1"),
                service.notifyPolicyRenewalDue(3L,1L,"POL-1"),
                service.notifyPolicyDocumentRequested(3L,1L,"POL-1"),
                service.notifyProductCreated(2L,"Flood Cover"),
                service.notifyProductUpdated(2L,"Flood Cover")).block();

        ArgumentCaptor<NotificationRequest> sent=ArgumentCaptor.forClass(NotificationRequest.class);
        verify(client,atLeastOnce()).sendNotification(sent.capture());
        assertThat(sent.getAllValues()).extracting(NotificationRequest::getType).containsExactly(
                "POLICY_SUBMITTED","POLICY_SUBMITTED","POLICY_SUBMITTED","POLICY_APPROVED","POLICY_REJECTED","POLICY_REJECTED",
                "POLICY_CANCELLED","POLICY_EXPIRED","POLICY_RENEWAL_DUE","POLICY_DOCUMENT_REQUESTED",
                "PRODUCT_CREATED","PRODUCT_UPDATED");
        assertThat(sent.getAllValues().get(1).getRecipientRole()).isEqualTo("ROLE_UNDERWRITER");
        assertThat(sent.getAllValues().get(2).getRecipientRole()).isEqualTo("ROLE_ADMIN");
        assertThat(sent.getAllValues().get(4).getMessage()).endsWith("Reason: Outside cover area");
        assertThat(sent.getAllValues().get(5).getMessage()).doesNotContain("Reason");
    }

    @Test
    void aFailedNotificationNeverBreaksTheBusinessAction(){
        when(client.sendNotification(any())).thenReturn(Mono.error(new IllegalStateException("down")));
        when(client.sendAuditEvent(any())).thenReturn(Mono.error(new IllegalStateException("down")));

        StepVerifier.create(service.notifyPolicyApproved(3L,1L,"POL-1")).verifyComplete();
        StepVerifier.create(service.notifyProductCreated(2L,"Flood Cover")).verifyComplete();
        StepVerifier.create(service.audit("POLICY_APPROVED","POLICY",1L,null,"Approved")).verifyComplete();
    }

    @Test
    void auditRecordsTheSignedInUserOrTheSystem(){
        when(client.sendAuditEvent(any())).thenReturn(Mono.empty());
        UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken(
                "5",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        service.auditChange("PRODUCT_UPDATED","PRODUCT",2L,null,"Threshold changed","150","200")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(admin)).block();
        service.audit("POLICY_EXPIRED","POLICY",1L,null,"Expired").block();

        ArgumentCaptor<AuditEventRequest> sent=ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(client,atLeastOnce()).sendAuditEvent(sent.capture());
        AuditEventRequest byAdmin=sent.getAllValues().get(0);
        assertThat(byAdmin.getActorUserId()).isEqualTo(5L);
        assertThat(byAdmin.getActorRole()).isEqualTo("ROLE_ADMIN");
        assertThat(byAdmin.getOldValue()).isEqualTo("150");
        assertThat(byAdmin.getNewValue()).isEqualTo("200");
        assertThat(sent.getAllValues().get(1).getActorRole()).isEqualTo("SYSTEM");
    }
}
