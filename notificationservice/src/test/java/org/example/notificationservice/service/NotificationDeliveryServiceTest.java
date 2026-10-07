package org.example.notificationservice.service;

import org.example.notificationservice.model.DeliveryAttempt;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.DeliveryStatus;
import org.example.notificationservice.model.Notification;
import org.example.notificationservice.repository.DeliveryAttemptRepository;
import org.example.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Delivery over each channel, the attempt log, and the scheduled retry and cleanup jobs. */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest{

    @Mock
    private DeliveryAttemptRepository attemptRepository;
    @Mock
    private EmailDeliveryService emailService;
    @Mock
    private SmsDeliveryService smsService;
    @Mock
    private NotificationRepository notificationRepository;

    private DeliveryAttemptService attemptService;
    private NotificationDeliveryService deliveryService;

    @BeforeEach
    void setUp(){
        attemptService=new DeliveryAttemptService(attemptRepository);
        deliveryService=new NotificationDeliveryService(attemptService,emailService,smsService);
    }

    private static Notification notification(DeliveryChannel channel){
        return Notification.builder().id(4L).recipientCustomerId(30L).title("Hi").message("Body")
                .deliveryChannel(channel).deliveryStatus(DeliveryStatus.CREATED).build();
    }

    @Test
    void everyChannelIsDeliveredAndLogged(){
        when(attemptRepository.save(any(DeliveryAttempt.class))).thenAnswer(i->Mono.just(i.getArgument(0)));
        when(emailService.sendEmail(anyString(),anyString(),anyString())).thenReturn(Mono.empty());
        when(smsService.sendSms(anyString(),anyString())).thenReturn(Mono.empty());

        for(DeliveryChannel channel:DeliveryChannel.values()){
            StepVerifier.create(deliveryService.dispatch(notification(channel)))
                    .assertNext(n->assertThat(n.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED))
                    .verifyComplete();
        }
        verify(emailService).sendEmail("customer-30","Hi","Body");
        verify(smsService).sendSms("customer-30","Body");

        ArgumentCaptor<DeliveryAttempt> attempt=ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptRepository,org.mockito.Mockito.times(DeliveryChannel.values().length)).save(attempt.capture());
        assertThat(attempt.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void aFailedSendIsMarkedFailedWithTheReason(){
        when(attemptRepository.save(any(DeliveryAttempt.class))).thenAnswer(i->Mono.just(i.getArgument(0)));
        when(emailService.sendEmail(anyString(),anyString(),anyString())).thenReturn(Mono.error(new IllegalStateException("smtp down")));
        when(smsService.sendSms(anyString(),anyString())).thenReturn(Mono.error(new IllegalStateException()));
        Notification email=notification(DeliveryChannel.EMAIL);
        email.setRecipientCustomerId(null);
        email.setRecipientUserId(2L);

        StepVerifier.create(deliveryService.dispatch(email))
                .assertNext(n->assertThat(n.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED))
                .verifyComplete();
        Notification sms=notification(DeliveryChannel.SMS);
        sms.setRecipientCustomerId(null);
        StepVerifier.create(deliveryService.dispatch(sms))
                .assertNext(n->assertThat(n.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED))
                .verifyComplete();

        verify(emailService).sendEmail("user-2","Hi","Body");
        verify(smsService).sendSms("recipient","Body");
        ArgumentCaptor<DeliveryAttempt> attempt=ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(attemptRepository,org.mockito.Mockito.atLeast(2)).save(attempt.capture());
        assertThat(attempt.getAllValues())
                .filteredOn(a->a.getDeliveryStatus()==DeliveryStatus.FAILED)
                .extracting(DeliveryAttempt::getErrorMessage)
                .containsExactly("smtp down","Delivery failed");
    }

    @Test
    void attemptsAreListedForANotification(){
        when(attemptRepository.findByNotificationIdOrderByAttemptNumberDesc(4L)).thenReturn(Flux.just(
                DeliveryAttempt.builder().id(1L).notificationId(4L).deliveryChannel(DeliveryChannel.EMAIL)
                        .deliveryStatus(DeliveryStatus.FAILED).attemptNumber(1).errorMessage("smtp down").build()));

        StepVerifier.create(attemptService.getByNotificationId(4L))
                .assertNext(a->{
                    assertThat(a.getDeliveryChannel()).isEqualTo(DeliveryChannel.EMAIL);
                    assertThat(a.getErrorMessage()).isEqualTo("smtp down");
                })
                .verifyComplete();
    }

    @Test
    void schedulerRetriesFailedDeliveriesWithinTheirRetryLimit(){
        Notification retryable=notification(DeliveryChannel.IN_APP);
        retryable.setDeliveryStatus(DeliveryStatus.FAILED);
        Notification exhausted=notification(DeliveryChannel.IN_APP);
        exhausted.setRetryCount(3);
        when(notificationRepository.findAllByDeliveryStatusOrderByCreatedAtDesc(DeliveryStatus.FAILED))
                .thenReturn(Flux.just(retryable,exhausted));
        when(attemptRepository.save(any(DeliveryAttempt.class))).thenAnswer(i->Mono.just(i.getArgument(0)));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i->Mono.just(i.getArgument(0)));

        new NotificationScheduler(notificationRepository,attemptRepository,deliveryService).retryFailedDeliveries();

        assertThat(retryable.getRetryCount()).isEqualTo(1);
        assertThat(retryable.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(exhausted.getRetryCount()).isEqualTo(3);
        verify(notificationRepository).save(retryable);
        verify(notificationRepository,never()).save(exhausted);
    }

    @Test
    void schedulerDeletesOnlyOldReadOrFailedNotificationsAndOldAttempts(){
        LocalDateTime old=LocalDateTime.now().minusDays(120);
        Notification oldRead=Notification.builder().id(1L).read(true).createdAt(old).build();
        Notification oldUnread=Notification.builder().id(2L).read(false).deliveryStatus(DeliveryStatus.DELIVERED).createdAt(old).build();
        Notification newRead=Notification.builder().id(3L).read(true).createdAt(LocalDateTime.now()).build();
        DeliveryAttempt oldAttempt=DeliveryAttempt.builder().id(9L).attemptedAt(old).build();
        DeliveryAttempt newAttempt=DeliveryAttempt.builder().id(10L).attemptedAt(LocalDateTime.now()).build();
        when(notificationRepository.findAll()).thenReturn(Flux.just(oldRead,oldUnread,newRead));
        when(notificationRepository.delete(any(Notification.class))).thenReturn(Mono.empty());
        when(attemptRepository.findAll()).thenReturn(Flux.just(oldAttempt,newAttempt));
        when(attemptRepository.delete(any(DeliveryAttempt.class))).thenReturn(Mono.empty());

        new NotificationScheduler(notificationRepository,attemptRepository,deliveryService).cleanupOldNotifications();

        verify(notificationRepository).delete(oldRead);
        verify(notificationRepository,never()).delete(oldUnread);
        verify(notificationRepository,never()).delete(newRead);
        verify(attemptRepository).delete(oldAttempt);
        verify(attemptRepository,never()).delete(newAttempt);
    }
}
