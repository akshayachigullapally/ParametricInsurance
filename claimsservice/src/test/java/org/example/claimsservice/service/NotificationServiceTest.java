package org.example.claimsservice.service;

import org.example.claimsservice.dto.NotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest{
    @Mock
    private NotificationServiceClient client;

    private NotificationService service;

    @BeforeEach
    void setUp(){
        service=new NotificationService(client);
        lenient().when(client.sendNotification(any())).thenReturn(Mono.empty());
    }

    private NotificationRequest sentNotification(Supplier<Mono<Void>> call){
        StepVerifier.create(call.get()).verifyComplete();
        ArgumentCaptor<NotificationRequest> captor=ArgumentCaptor.forClass(NotificationRequest.class);
        verify(client).sendNotification(captor.capture());
        return captor.getValue();
    }

    @Test
    void notifyClaimSubmitted_shouldTargetCustomer(){
        NotificationRequest request=sentNotification(()->service.notifyClaimSubmitted(3L,10L,"CLM-1"));

        assertThat(request.getRecipientCustomerId()).isEqualTo(3L);
        assertThat(request.getType()).isEqualTo("CLAIM_SUBMITTED");
        assertThat(request.getRelatedEntityType()).isEqualTo("CLAIM");
        assertThat(request.getRelatedEntityId()).isEqualTo(10L);
        assertThat(request.getMessage()).contains("CLM-1");
    }

    @Test
    void notifyManualClaimSubmitted_shouldUseManualType(){
        assertThat(sentNotification(()->service.notifyManualClaimSubmitted(3L,10L,"CLM-1")).getType())
                .isEqualTo("MANUAL_CLAIM_SUBMITTED");
    }

    @Test
    void notifyClaimUnderReview_shouldUseReviewType(){
        assertThat(sentNotification(()->service.notifyClaimUnderReview(3L,10L,"CLM-1")).getType())
                .isEqualTo("CLAIM_UNDER_REVIEW");
    }

    @Test
    void notifyClaimRejected_shouldIncludeReason(){
        NotificationRequest request=sentNotification(()->service.notifyClaimRejected(3L,10L,"CLM-1","No loss"));

        assertThat(request.getType()).isEqualTo("CLAIM_REJECTED");
        assertThat(request.getMessage()).contains("No loss");
    }

    @Test
    void notifyClaimSettled_shouldUseSettledType(){
        assertThat(sentNotification(()->service.notifyClaimSettled(3L,10L,"CLM-1")).getType())
                .isEqualTo("CLAIM_SETTLED");
    }

    @Test
    void notifyClaimCancelled_shouldUseCancelledType(){
        assertThat(sentNotification(()->service.notifyClaimCancelled(3L,10L,"CLM-1")).getType())
                .isEqualTo("CLAIM_CANCELLED");
    }

    @Test
    void notify_whenClientFails_shouldSwallowError(){
        when(client.sendNotification(any())).thenReturn(Mono.error(new IllegalStateException("down")));

        StepVerifier.create(service.notifyClaimApproved(3L,10L,"CLM-1")).verifyComplete();
    }

    @Test
    void notifyRole_whenClientFails_shouldSwallowError(){
        when(client.sendNotification(any())).thenReturn(Mono.error(new IllegalStateException("down")));

        StepVerifier.create(service.notifyClaimPendingReview(10L,"CLM-1")).verifyComplete();
    }
}
