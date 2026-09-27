package org.example.productservice.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.example.productservice.dto.PolicyResponse;
import org.example.productservice.exception.ConflictException;
import org.example.productservice.model.Policy;
import org.example.productservice.model.PolicyStatus;
import org.example.productservice.repository.PolicyRepository;
import org.example.productservice.service.NotificationService;
import org.example.productservice.service.PolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PolicyRenewalSchedulerTest{
    private static final LocalDate TODAY=LocalDate.of(2026,9,27);

    private PolicyRepository policies;
    private PolicyService policyService;
    private NotificationService notifications;
    private PolicyRenewalScheduler scheduler;

    @BeforeEach
    void setUp(){
        policies=mock(PolicyRepository.class);
        policyService=mock(PolicyService.class);
        notifications=mock(NotificationService.class);
        when(notifications.notifyPolicyRenewalDue(any(),any(),any())).thenReturn(Mono.empty());
        scheduler=new PolicyRenewalScheduler(policies,policyService,notifications);
    }

    private Policy policy(long id,int daysLeft,boolean autoRenew){
        return Policy.builder().id(id).customerId(1L).policyNumber("POL-"+id)
                .status(PolicyStatus.ACTIVE.name()).autoRenew(autoRenew)
                .endDate(TODAY.plusDays(daysLeft)).build();
    }

    @Test
    void autoRenewPoliciesGetRenewalApplicationWithin14Days(){
        Policy due=policy(1,10,true);
        Policy early=policy(2,40,true);
        when(policies.findAllByStatus("ACTIVE")).thenReturn(Flux.just(due,early));
        when(policyService.createAutomaticRenewal(due))
                .thenReturn(Mono.just(PolicyResponse.builder().policyNumber("POL-NEW").build()));

        StepVerifier.create(scheduler.sweep(TODAY)).verifyComplete();

        verify(policyService).createAutomaticRenewal(due);
        verify(policyService,never()).createAutomaticRenewal(early);
    }

    @Test
    void alreadyRenewedPoliciesAreSkippedQuietly(){
        Policy due=policy(1,5,true);
        when(policies.findAllByStatus("ACTIVE")).thenReturn(Flux.just(due));
        when(policyService.createAutomaticRenewal(due))
                .thenReturn(Mono.error(new ConflictException("This policy has already been renewed")));

        StepVerifier.create(scheduler.sweep(TODAY)).verifyComplete();
    }

    @Test
    void remindersGoOut14And3DaysBeforeTheEnd(){
        when(policies.findAllByStatus("ACTIVE")).thenReturn(Flux.just(
                policy(1,14,false),policy(2,3,false),policy(3,7,false)));

        StepVerifier.create(scheduler.sweep(TODAY)).verifyComplete();

        verify(notifications).notifyPolicyRenewalDue(1L,1L,"POL-1");
        verify(notifications).notifyPolicyRenewalDue(1L,2L,"POL-2");
        verify(notifications,never()).notifyPolicyRenewalDue(1L,3L,"POL-3");
    }
}
