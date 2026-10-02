package org.example.financialservice.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.financialservice.model.Payout;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.repository.PayoutRepository;
import org.example.financialservice.service.PayoutService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PayoutRetrySchedulerTest{
    private final PayoutRepository repository=mock(PayoutRepository.class);
    private final PayoutService service=mock(PayoutService.class);
    private final PayoutRetryScheduler scheduler=new PayoutRetryScheduler(repository,service);

    @Test
    void paysWaitingPayoutsAndRetriesFailedOnes(){
        Payout pending=Payout.builder().id(1L).payoutReference("PO-1").status(PayoutStatus.PENDING).build();
        Payout processing=Payout.builder().id(2L).payoutReference("PO-2").status(PayoutStatus.PROCESSING).build();
        Payout failed=Payout.builder().id(3L).payoutReference("PO-3").status(PayoutStatus.FAILED).retryCount(1).build();
        when(repository.findAllByStatus(PayoutStatus.PENDING)).thenReturn(Flux.just(pending));
        when(repository.findAllByStatus(PayoutStatus.PROCESSING)).thenReturn(Flux.just(processing));
        when(repository.findAllByStatusAndRetryCountLessThan(PayoutStatus.FAILED,3)).thenReturn(Flux.just(failed));
        when(service.disburse(any())).thenAnswer(inv->Mono.just(inv.getArgument(0)));
        when(service.retryAutomatically(any())).thenAnswer(inv->Mono.just(inv.getArgument(0)));

        scheduler.payOutstandingPayouts();

        verify(service).disburse(pending);
        verify(service).disburse(processing);
        verify(service).retryAutomatically(failed);
    }

    @Test
    void oneFailingPayoutDoesNotStopTheOthers(){
        Payout first=Payout.builder().id(1L).payoutReference("PO-1").build();
        Payout second=Payout.builder().id(2L).payoutReference("PO-2").build();
        when(repository.findAllByStatus(PayoutStatus.PENDING)).thenReturn(Flux.just(first,second));
        when(repository.findAllByStatus(PayoutStatus.PROCESSING)).thenReturn(Flux.empty());
        when(repository.findAllByStatusAndRetryCountLessThan(PayoutStatus.FAILED,3)).thenReturn(Flux.empty());
        when(service.disburse(first)).thenReturn(Mono.error(new RuntimeException("db down")));
        when(service.disburse(second)).thenReturn(Mono.just(second));

        scheduler.payOutstandingPayouts();

        verify(service).disburse(second);
        verify(service,never()).retryAutomatically(any());
    }
}
