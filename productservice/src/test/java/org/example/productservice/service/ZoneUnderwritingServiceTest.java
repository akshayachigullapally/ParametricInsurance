package org.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.example.productservice.client.TriggerServiceClient;
import org.example.productservice.dto.InternalZoneResponse;
import org.example.productservice.exception.ConflictException;
import org.example.productservice.model.Policy;
import org.example.productservice.model.PolicyStatus;
import org.example.productservice.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class ZoneUnderwritingServiceTest{
    @Mock private PolicyRepository policyRepository;
    @Mock private TriggerServiceClient triggerServiceClient;
    @InjectMocks private ZoneUnderwritingService service;

    @Test
    void weatherAlert_blocksNewSales(){
        when(triggerServiceClient.getZoneById(1L)).thenReturn(Mono.just(zone(false,null)
                .toBuilder().weatherAlert(true).alertMessage("Cyclone warning").build()));

        StepVerifier.create(service.checkCanSell(1L,new BigDecimal("100000"),List.of()))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("New policies cannot be sold in Hyderabad Central while a weather alert is active: Cyclone warning");
                })
                .verify();
    }

    @Test
    void cap_blocksSaleThatWouldExceedIt(){
        when(triggerServiceClient.getZoneById(1L)).thenReturn(Mono.just(zone(false,"500000")));
        stubOpen(List.of(policy(10L,"300000")),List.of(policy(11L,"100000")));

        StepVerifier.create(service.checkCanSell(1L,new BigDecimal("100001"),List.of()))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Exposure cap reached for Hyderabad Central: ₹400000 of ₹500000 is already committed, only ₹100000 of cover is still available");
                })
                .verify();
    }

    @Test
    void exposureReport_computesUsageAgainstTheCap(){
        when(triggerServiceClient.getActiveZones()).thenReturn(Flux.just(
                zone(false,"1000000"),
                zone(true,null).toBuilder().zoneId(2L).zoneName("Mumbai Central").build()));
        stubOpen(List.of(policy(10L,"300000")),List.of(policy(11L,"150000")));
        when(policyRepository.findAllByLocationZoneIdAndStatus(2L,PolicyStatus.ACTIVE.name())).thenReturn(Flux.empty());
        when(policyRepository.findAllByLocationZoneIdAndStatus(2L,PolicyStatus.PENDING.name())).thenReturn(Flux.empty());

        StepVerifier.create(service.exposureReport())
                .assertNext(report->{
                    assertThat(report).hasSize(2);
                    var hyderabad=report.get(0);
                    assertThat(hyderabad.getUsedExposure()).isEqualByComparingTo("450000");
                    assertThat(hyderabad.getRemainingExposure()).isEqualByComparingTo("550000");
                    assertThat(hyderabad.getUtilizationPercent()).isEqualByComparingTo("45.0");
                    assertThat(hyderabad.getActivePolicyCount()).isEqualTo(1);
                    assertThat(hyderabad.getPendingPolicyCount()).isEqualTo(1);
                    assertThat(hyderabad.isSalesOpen()).isTrue();

                    var mumbai=report.get(1);
                    assertThat(mumbai.getMaxExposure()).isNull();
                    assertThat(mumbai.getUtilizationPercent()).isNull();
                    assertThat(mumbai.isSalesOpen()).isFalse();
                })
                .verifyComplete();
    }

    private void stubOpen(List<Policy> active,List<Policy> pending){
        when(policyRepository.findAllByLocationZoneIdAndStatus(1L,PolicyStatus.ACTIVE.name()))
                .thenAnswer(invocation->Flux.fromIterable(active));
        when(policyRepository.findAllByLocationZoneIdAndStatus(1L,PolicyStatus.PENDING.name()))
                .thenAnswer(invocation->Flux.fromIterable(pending));
    }

    private static InternalZoneResponse zone(boolean alert,String cap){
        return InternalZoneResponse.builder()
                .zoneId(1L)
                .zoneCode("HYDERABAD_CENTRAL")
                .zoneName("Hyderabad Central")
                .status("ACTIVE")
                .weatherAlert(alert)
                .maxExposure(cap==null?null:new BigDecimal(cap))
                .build();
    }

    private static Policy policy(Long id,String sumInsured){
        return Policy.builder().id(id).locationZoneId(1L).sumInsured(new BigDecimal(sumInsured)).build();
    }
}
