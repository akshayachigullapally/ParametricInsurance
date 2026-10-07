package org.example.notificationservice.service;

import org.example.notificationservice.dto.UpsertNotificationPreferenceRequest;
import org.example.notificationservice.model.NotificationPreference;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest{

    @Mock
    private NotificationPreferenceRepository repository;

    private NotificationPreferenceService service;

    @BeforeEach
    void setUp(){
        service=new NotificationPreferenceService(repository);
    }

    private static Context user(long userId,Long customerId){
        UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),null,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        auth.setDetails(customerId);
        return ReactiveSecurityContextHolder.withAuthentication(auth);
    }

    @Test
    void firstUpsertCreatesAPreferenceWithSensibleDefaults(){
        when(repository.findByUserIdAndNotificationType(7L,NotificationType.POLICY_APPROVED)).thenReturn(Mono.empty());
        when(repository.save(any(NotificationPreference.class))).thenAnswer(i->Mono.just(i.getArgument(0)));

        UpsertNotificationPreferenceRequest request=UpsertNotificationPreferenceRequest.builder()
                .notificationType(NotificationType.POLICY_APPROVED).emailEnabled(true).build();

        StepVerifier.create(service.upsert(request).contextWrite(user(7L,30L)))
                .assertNext(p->{
                    assertThat(p.getUserId()).isEqualTo(7L);
                    assertThat(p.getCustomerId()).isEqualTo(30L);
                    assertThat(p.isInAppEnabled()).isTrue();
                    assertThat(p.isEmailEnabled()).isTrue();
                    assertThat(p.isSmsEnabled()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void upsertChangesOnlyTheChannelsThatWereSent(){
        NotificationPreference existing=NotificationPreference.builder().id(1L).userId(7L)
                .notificationType(NotificationType.POLICY_APPROVED).inAppEnabled(true).emailEnabled(true).smsEnabled(false).build();
        when(repository.findByUserIdAndNotificationType(7L,NotificationType.POLICY_APPROVED)).thenReturn(Mono.just(existing));
        when(repository.save(any(NotificationPreference.class))).thenAnswer(i->Mono.just(i.getArgument(0)));
        when(repository.findAll()).thenReturn(Flux.just(existing,
                NotificationPreference.builder().id(2L).userId(8L).build()));

        UpsertNotificationPreferenceRequest request=UpsertNotificationPreferenceRequest.builder()
                .notificationType(NotificationType.POLICY_APPROVED).inAppEnabled(false).smsEnabled(true).build();

        StepVerifier.create(service.upsert(request).contextWrite(user(7L,null)))
                .assertNext(p->{
                    assertThat(p.isInAppEnabled()).isFalse();
                    assertThat(p.isEmailEnabled()).isTrue();
                    assertThat(p.isSmsEnabled()).isTrue();
                    assertThat(p.getUpdatedAt()).isNotNull();
                })
                .verifyComplete();
        StepVerifier.create(service.myPreferences().contextWrite(user(7L,null)))
                .assertNext(p->assertThat(p.getId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void channelsAreEnabledUnlessTheUserTurnedThemOff(){
        NotificationPreference pref=NotificationPreference.builder().userId(7L)
                .inAppEnabled(false).emailEnabled(true).smsEnabled(false).build();
        when(repository.findByUserIdAndNotificationType(7L,NotificationType.POLICY_APPROVED)).thenReturn(Mono.just(pref));
        when(repository.findByUserIdAndNotificationType(8L,NotificationType.POLICY_APPROVED)).thenReturn(Mono.empty());

        StepVerifier.create(service.isEnabled(7L,NotificationType.POLICY_APPROVED,"IN_APP")).expectNext(false).verifyComplete();
        StepVerifier.create(service.isEnabled(7L,NotificationType.POLICY_APPROVED,"EMAIL")).expectNext(true).verifyComplete();
        StepVerifier.create(service.isEnabled(7L,NotificationType.POLICY_APPROVED,"SMS")).expectNext(false).verifyComplete();
        StepVerifier.create(service.isEnabled(7L,NotificationType.POLICY_APPROVED,"PUSH")).expectNext(true).verifyComplete();
        StepVerifier.create(service.isEnabled(8L,NotificationType.POLICY_APPROVED,"EMAIL")).expectNext(true).verifyComplete();
    }
}
