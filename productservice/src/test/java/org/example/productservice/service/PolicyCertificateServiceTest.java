package org.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.example.productservice.client.TriggerServiceClient;
import org.example.productservice.dto.InternalZoneResponse;
import org.example.productservice.exception.ConflictException;
import org.example.productservice.model.Policy;
import org.example.productservice.model.PolicyStatus;
import org.example.productservice.model.PolicyTriggerSnapshot;
import org.example.productservice.repository.PolicyRepository;
import org.example.productservice.repository.PolicyTriggerSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PolicyCertificateServiceTest{
    private PolicyRepository policies;
    private PolicyTriggerSnapshotRepository snapshots;
    private TriggerServiceClient triggers;
    private PolicyCertificateService service;

    @BeforeEach
    void setUp(){
        policies=mock(PolicyRepository.class);
        snapshots=mock(PolicyTriggerSnapshotRepository.class);
        triggers=mock(TriggerServiceClient.class);
        service=new PolicyCertificateService(policies,snapshots,triggers);
    }

    private Policy policy(String status){
        return Policy.builder().id(100L).policyNumber("POL-1").customerId(1L).productId(10L)
                .productCode("FLOOD-001").productName("Flood Protection Plan").locationZoneId(4L)
                .sumInsured(new BigDecimal("100000")).premiumAmount(new BigDecimal("1875"))
                .termMonths(6).status(status).startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(6))
                .build();
    }

    @Test
    void rendersCertificateForActivePolicy(){
        when(policies.findByIdAndCustomerId(100L,1L)).thenReturn(Mono.just(policy(PolicyStatus.ACTIVE.name())));
        when(snapshots.findByPolicyId(100L)).thenReturn(Mono.just(PolicyTriggerSnapshot.builder()
                .measurementType("WATER_LEVEL").measurementUnit("m").aggregationType("MAX")
                .comparisonOperator("GREATER_THAN_OR_EQUAL").thresholdValue(new BigDecimal("5.00"))
                .observationWindowHours(6).build()));
        when(triggers.getZoneById(4L)).thenReturn(Mono.just(InternalZoneResponse.builder()
                .zoneName("Hyderabad Central").zoneCode("HYD_C").state("Telangana").country("India").build()));

        StepVerifier.create(service.forCustomer(1L,100L))
                .assertNext(certificate->{
                    assertThat(certificate.fileName()).isEqualTo("POL-1-certificate.pdf");
                    assertThat(new String(certificate.bytes(),0,5,StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
                })
                .verifyComplete();
    }

    @Test
    void stillRendersWhenZoneLookupFails(){
        when(policies.findByIdAndCustomerId(100L,1L)).thenReturn(Mono.just(policy(PolicyStatus.EXPIRED.name())));
        when(snapshots.findByPolicyId(100L)).thenReturn(Mono.empty());
        when(triggers.getZoneById(4L)).thenReturn(Mono.error(new RuntimeException("down")));

        StepVerifier.create(service.forCustomer(1L,100L))
                .assertNext(certificate->assertThat(certificate.bytes().length).isGreaterThan(500))
                .verifyComplete();
    }

    @Test
    void refusesCertificateForPendingApplication(){
        when(policies.findByIdAndCustomerId(100L,1L)).thenReturn(Mono.just(policy(PolicyStatus.PENDING.name())));

        StepVerifier.create(service.forCustomer(1L,100L))
                .expectError(ConflictException.class)
                .verify();
    }
}
