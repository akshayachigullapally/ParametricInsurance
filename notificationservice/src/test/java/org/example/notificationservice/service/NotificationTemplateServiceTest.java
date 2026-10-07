package org.example.notificationservice.service;

import org.example.notificationservice.dto.CreateNotificationTemplateRequest;
import org.example.notificationservice.exception.NotFoundException;
import org.example.notificationservice.model.DefaultTemplate;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.NotificationTemplate;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.repository.NotificationTemplateDataRepository;
import org.example.notificationservice.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateServiceTest{

    @Mock
    private NotificationTemplateRepository repository;
    @Mock
    private NotificationTemplateDataRepository dataRepository;

    private NotificationTemplateService service;

    @BeforeEach
    void setUp(){
        service=new NotificationTemplateService(repository,dataRepository);
    }

    private static CreateNotificationTemplateRequest request(DeliveryChannel channel){
        return CreateNotificationTemplateRequest.builder()
                .templateCode("  POLICY_WELCOME ")
                .notificationType(NotificationType.POLICY_APPROVED)
                .titleTemplate(" Welcome ")
                .messageTemplate(" Your policy {policyNumber} is active ")
                .defaultChannel(channel)
                .build();
    }

    private static NotificationTemplate template(){
        return NotificationTemplate.builder().id(1L).templateCode("OLD").notificationType(NotificationType.POLICY_SUBMITTED)
                .titleTemplate("t").messageTemplate("m").defaultChannel(DeliveryChannel.EMAIL).active(true).build();
    }

    @Test
    void createAndUpdateTrimTheTextAndDefaultTheChannel(){
        when(repository.save(any(NotificationTemplate.class))).thenAnswer(i->Mono.just(i.getArgument(0)));
        when(repository.findById(1L)).thenReturn(Mono.just(template()));

        StepVerifier.create(service.create(request(null)))
                .assertNext(r->{
                    assertThat(r.getTemplateCode()).isEqualTo("POLICY_WELCOME");
                    assertThat(r.getTitleTemplate()).isEqualTo("Welcome");
                    assertThat(r.getDefaultChannel()).isEqualTo(DeliveryChannel.IN_APP);
                    assertThat(r.isActive()).isTrue();
                })
                .verifyComplete();
        StepVerifier.create(service.update(1L,request(DeliveryChannel.SMS)))
                .assertNext(r->{
                    assertThat(r.getTemplateCode()).isEqualTo("POLICY_WELCOME");
                    assertThat(r.getNotificationType()).isEqualTo(NotificationType.POLICY_APPROVED);
                    assertThat(r.getDefaultChannel()).isEqualTo(DeliveryChannel.SMS);
                    assertThat(r.getUpdatedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void getListActivateAndDeactivate(){
        NotificationTemplate t=template();
        when(repository.findById(1L)).thenReturn(Mono.just(t));
        when(repository.findAll()).thenReturn(Flux.just(t));
        when(repository.save(any(NotificationTemplate.class))).thenAnswer(i->Mono.just(i.getArgument(0)));

        StepVerifier.create(service.get(1L)).assertNext(r->assertThat(r.getId()).isEqualTo(1L)).verifyComplete();
        StepVerifier.create(service.all()).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.deactivate(1L)).verifyComplete();
        assertThat(t.isActive()).isFalse();
        StepVerifier.create(service.activate(1L)).verifyComplete();
        assertThat(t.isActive()).isTrue();
    }

    @Test
    void unknownTemplateIdIsNotFound(){
        when(repository.findById(9L)).thenReturn(Mono.empty());

        StepVerifier.create(service.get(9L)).expectError(NotFoundException.class).verify();
        StepVerifier.create(service.update(9L,request(null))).expectError(NotFoundException.class).verify();
        StepVerifier.create(service.activate(9L)).expectError(NotFoundException.class).verify();
        StepVerifier.create(service.deactivate(9L)).expectError(NotFoundException.class).verify();
    }

    @Test
    void findByCodeFallsBackToTheBuiltInDefaultTemplate(){
        DefaultTemplate builtIn=DefaultTemplate.builder().id(3L).notificationType(NotificationType.POLICY_APPROVED)
                .titleTemplate("Approved").messageTemplate("Policy {policyNumber} approved")
                .defaultChannel(DeliveryChannel.IN_APP).active(true).build();
        when(repository.findByTemplateCodeAndActiveTrue("CUSTOM")).thenReturn(Mono.just(template()));
        when(repository.findByTemplateCodeAndActiveTrue("policy_approved")).thenReturn(Mono.empty());
        when(repository.findByTemplateCodeAndActiveTrue("NOT_A_TYPE")).thenReturn(Mono.empty());
        when(dataRepository.findByNotificationType(NotificationType.POLICY_APPROVED)).thenReturn(Mono.just(builtIn));
        when(dataRepository.findAll()).thenReturn(Flux.just(builtIn));
        when(repository.findByNotificationTypeAndActiveTrue(NotificationType.POLICY_SUBMITTED)).thenReturn(Flux.just(template()));

        StepVerifier.create(service.findByCode(" CUSTOM ")).assertNext(t->assertThat(t.getTemplateCode()).isEqualTo("OLD")).verifyComplete();
        StepVerifier.create(service.findByCode("policy_approved"))
                .assertNext(t->{
                    assertThat(t.getTemplateCode()).isEqualTo("POLICY_APPROVED");
                    assertThat(t.getTitleTemplate()).isEqualTo("Approved");
                })
                .verifyComplete();
        StepVerifier.create(service.findByCode("NOT_A_TYPE")).verifyComplete();
        StepVerifier.create(service.defaults())
                .assertNext(r->assertThat(r.getTemplateCode()).isEqualTo("POLICY_APPROVED"))
                .verifyComplete();
        StepVerifier.create(service.findByType(NotificationType.POLICY_SUBMITTED)).expectNextCount(1).verifyComplete();
    }
}
