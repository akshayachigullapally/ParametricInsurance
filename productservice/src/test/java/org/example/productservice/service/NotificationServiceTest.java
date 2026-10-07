package org.example.productservice.service;

import org.example.productservice.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;


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

        StepVerifier.create(service.notifyPolicyApproved(3L,1L,"POL-1")).verifyComplete();
        StepVerifier.create(service.notifyProductCreated(2L,"Flood Cover")).verifyComplete();
    }
}
