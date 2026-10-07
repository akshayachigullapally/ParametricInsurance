package org.example.productservice.config;

import org.example.productservice.model.Policy;
import org.example.productservice.model.PolicyStatusHistory;
import org.example.productservice.repository.PolicyRepository;
import org.example.productservice.repository.PolicyStatusHistoryRepository;
import org.example.productservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyExpirySchedulerTest{

    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private PolicyStatusHistoryRepository historyRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PolicyExpiryScheduler scheduler;

    @Test
    void expiresOnlyActivePoliciesWhoseTermHasEnded(){
        Policy ended=Policy.builder().id(1L).customerId(3L).policyNumber("POL-1").status("ACTIVE").endDate(LocalDate.now()).build();
        Policy running=Policy.builder().id(2L).customerId(3L).policyNumber("POL-2").status("ACTIVE").endDate(LocalDate.now().plusDays(5)).build();
        Policy noEnd=Policy.builder().id(3L).status("ACTIVE").build();
        when(policyRepository.findAllByStatus("ACTIVE")).thenReturn(Flux.just(ended,running,noEnd));
        when(policyRepository.save(any(Policy.class))).thenAnswer(i->Mono.just(i.getArgument(0)));
        when(historyRepository.save(any(PolicyStatusHistory.class))).thenAnswer(i->Mono.just(i.getArgument(0)));
        when(notificationService.notifyPolicyExpired(3L,1L,"POL-1")).thenReturn(Mono.empty());

        StepVerifier.create(scheduler.expirePolicies()).verifyComplete();

        assertThat(ended.getStatus()).isEqualTo("EXPIRED");
        assertThat(running.getStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<PolicyStatusHistory> history=ArgumentCaptor.forClass(PolicyStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getOldStatus()).isEqualTo("ACTIVE");
        assertThat(history.getValue().getChangeReason()).isEqualTo("Policy term expired");
        verify(policyRepository,never()).save(running);
    }
}
