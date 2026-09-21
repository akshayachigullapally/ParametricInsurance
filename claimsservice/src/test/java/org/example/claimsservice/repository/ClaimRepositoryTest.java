package org.example.claimsservice.repository;

import org.example.claimsservice.model.Claim;
import org.example.claimsservice.model.ClaimStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@ActiveProfiles("test")
class ClaimRepositoryTest {
    @Autowired private ClaimRepository claimRepository;
    @Autowired private ClaimTimelineRepository claimTimelineRepository;

    @BeforeEach
    void cleanDatabase() {
        StepVerifier.create(
                claimTimelineRepository.deleteAll()
                        .then(claimRepository.deleteAll())
        ).verifyComplete();
    }

    @Test
    void findByClaimNumber_whenClaimExists_shouldReturnClaim() {
        Mono<Claim> operation=claimRepository
                .save(claim("CLM-001",1L,100L,500L,ClaimStatus.SUBMITTED,false))
                .then(claimRepository.findByClaimNumber("CLM-001"));

        StepVerifier.create(operation)
                .assertNext(found->{
                    assertThat(found.getId()).isNotNull();
                    assertThat(found.getClaimNumber()).isEqualTo("CLM-001");
                    assertThat(found.getCustomerId()).isEqualTo(1L);
                    assertThat(found.getPolicyId()).isEqualTo(100L);
                    assertThat(found.getStatus()).isEqualTo(ClaimStatus.SUBMITTED.name());
                })
                .verifyComplete();
    }

    @Test
    void findByClaimNumber_whenClaimDoesNotExist_shouldReturnEmpty() {
        StepVerifier.create(claimRepository.findByClaimNumber("UNKNOWN"))
                .verifyComplete();
    }

    @Test
    void existsByClaimNumber_whenClaimExists_shouldReturnTrue() {
        Mono<Boolean> operation=claimRepository
                .save(claim("CLM-001",1L,100L,500L,ClaimStatus.SUBMITTED,false))
                .then(claimRepository.existsByClaimNumber("CLM-001"));

        StepVerifier.create(operation)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void existsByClaimNumber_whenClaimDoesNotExist_shouldReturnFalse() {
        StepVerifier.create(claimRepository.existsByClaimNumber("UNKNOWN"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void findAllByCustomerId_shouldReturnOnlyCustomerClaims() {
        Mono<List<Claim>> operation=claimRepository.saveAll(Flux.just(
                        claim("CLM-001",10L,100L,501L,ClaimStatus.SUBMITTED,false),
                        claim("CLM-002",10L,101L,502L,ClaimStatus.APPROVED,false),
                        claim("CLM-003",20L,102L,503L,ClaimStatus.REJECTED,false)
                ))
                .thenMany(claimRepository.findAllByCustomerId(10L))
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->{
                    assertThat(result).hasSize(2);
                    assertThat(result).extracting(Claim::getCustomerId).containsOnly(10L);
                    assertThat(result).extracting(Claim::getClaimNumber)
                            .containsExactlyInAnyOrder("CLM-001","CLM-002");
                })
                .verifyComplete();
    }

    @Test
    void findAllByCustomerIdAndStatus_shouldApplyBothFilters() {
        Mono<List<Claim>> operation=claimRepository.saveAll(Flux.just(
                        claim("CLM-001",10L,100L,501L,ClaimStatus.SUBMITTED,false),
                        claim("CLM-002",10L,101L,502L,ClaimStatus.APPROVED,false),
                        claim("CLM-003",10L,102L,503L,ClaimStatus.SUBMITTED,false),
                        claim("CLM-004",20L,103L,504L,ClaimStatus.SUBMITTED,false)
                ))
                .thenMany(claimRepository.findAllByCustomerIdAndStatus(
                        10L,ClaimStatus.SUBMITTED.name()))
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->{
                    assertThat(result).hasSize(2);
                    assertThat(result).extracting(Claim::getCustomerId).containsOnly(10L);
                    assertThat(result).extracting(Claim::getStatus)
                            .containsOnly(ClaimStatus.SUBMITTED.name());
                    assertThat(result).extracting(Claim::getClaimNumber)
                            .containsExactlyInAnyOrder("CLM-001","CLM-003");
                })
                .verifyComplete();
    }

    @Test
    void findAllByStatus_shouldReturnOnlyMatchingClaims() {
        Mono<List<Claim>> operation=claimRepository.saveAll(Flux.just(
                        claim("CLM-001",10L,100L,501L,ClaimStatus.APPROVED,false),
                        claim("CLM-002",20L,101L,502L,ClaimStatus.SUBMITTED,false),
                        claim("CLM-003",30L,102L,503L,ClaimStatus.APPROVED,false)
                ))
                .thenMany(claimRepository.findAllByStatus(ClaimStatus.APPROVED.name()))
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->{
                    assertThat(result).hasSize(2);
                    assertThat(result).extracting(Claim::getStatus)
                            .containsOnly(ClaimStatus.APPROVED.name());
                    assertThat(result).extracting(Claim::getClaimNumber)
                            .containsExactlyInAnyOrder("CLM-001","CLM-003");
                })
                .verifyComplete();
    }

    @Test
    void findByIdAndCustomerId_whenBothMatch_shouldReturnClaim() {
        Mono<Claim> operation=claimRepository
                .save(claim("CLM-001",10L,100L,500L,ClaimStatus.SUBMITTED,false))
                .flatMap(saved->claimRepository.findByIdAndCustomerId(saved.getId(),10L));

        StepVerifier.create(operation)
                .assertNext(found->{
                    assertThat(found.getId()).isNotNull();
                    assertThat(found.getClaimNumber()).isEqualTo("CLM-001");
                    assertThat(found.getCustomerId()).isEqualTo(10L);
                })
                .verifyComplete();
    }

    @Test
    void findByIdAndCustomerId_whenCustomerDoesNotMatch_shouldReturnEmpty() {
        Mono<Claim> operation=claimRepository
                .save(claim("CLM-001",10L,100L,500L,ClaimStatus.SUBMITTED,false))
                .flatMap(saved->claimRepository.findByIdAndCustomerId(saved.getId(),99L));

        StepVerifier.create(operation).verifyComplete();
    }

    @Test
    void existsByCustomerIdAndPolicyIdAndStatus_whenAllFieldsMatch_shouldReturnTrue() {
        Mono<Boolean> operation=claimRepository
                .save(claim("CLM-001",10L,100L,500L,ClaimStatus.SUBMITTED,false))
                .then(claimRepository.existsByCustomerIdAndPolicyIdAndStatus(
                        10L,100L,ClaimStatus.SUBMITTED.name()));

        StepVerifier.create(operation)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void existsByCustomerIdAndPolicyIdAndStatus_whenStatusDoesNotMatch_shouldReturnFalse() {
        Mono<Boolean> operation=claimRepository
                .save(claim("CLM-001",10L,100L,500L,ClaimStatus.SUBMITTED,false))
                .then(claimRepository.existsByCustomerIdAndPolicyIdAndStatus(
                        10L,100L,ClaimStatus.APPROVED.name()));

        StepVerifier.create(operation)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void existsByTriggerEventAndStatusNot_whenClaimIsNotCancelled_shouldReturnTrue() {
        Mono<Boolean> operation=claimRepository
                .save(claim("CLM-001",10L,100L,500L,ClaimStatus.SUBMITTED,false))
                .then(claimRepository
                        .existsByCustomerIdAndPolicyIdAndTriggerEventIdAndStatusNot(
                                10L,100L,500L,ClaimStatus.CANCELLED));

        StepVerifier.create(operation)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void existsByTriggerEventAndStatusNot_whenClaimIsCancelled_shouldReturnFalse() {
        Mono<Boolean> operation=claimRepository
                .save(claim("CLM-001",10L,100L,500L,ClaimStatus.CANCELLED,false))
                .then(claimRepository
                        .existsByCustomerIdAndPolicyIdAndTriggerEventIdAndStatusNot(
                                10L,100L,500L,ClaimStatus.CANCELLED));

        StepVerifier.create(operation)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void existsByTriggerEventAndStatusNot_whenTriggerEventDoesNotMatch_shouldReturnFalse() {
        Mono<Boolean> operation=claimRepository
                .save(claim("CLM-001",10L,100L,500L,ClaimStatus.SUBMITTED,false))
                .then(claimRepository
                        .existsByCustomerIdAndPolicyIdAndTriggerEventIdAndStatusNot(
                                10L,100L,999L,ClaimStatus.CANCELLED));

        StepVerifier.create(operation)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void existsByIncidentDateAndStatusNot_whenClaimMatches_shouldReturnTrue() {
        LocalDate incidentDate=LocalDate.of(2026,9,15);
        Claim claim=claim("CLM-001",10L,100L,500L,ClaimStatus.SUBMITTED,false);
        claim.setIncidentDate(incidentDate);

        Mono<Boolean> operation=claimRepository.save(claim)
                .then(claimRepository
                        .existsByCustomerIdAndPolicyIdAndIncidentDateAndStatusNot(
                                10L,100L,incidentDate,ClaimStatus.CANCELLED));

        StepVerifier.create(operation)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void existsByIncidentDateAndStatusNot_whenClaimIsCancelled_shouldReturnFalse() {
        LocalDate incidentDate=LocalDate.of(2026,9,15);
        Claim claim=claim("CLM-001",10L,100L,500L,ClaimStatus.CANCELLED,false);
        claim.setIncidentDate(incidentDate);

        Mono<Boolean> operation=claimRepository.save(claim)
                .then(claimRepository
                        .existsByCustomerIdAndPolicyIdAndIncidentDateAndStatusNot(
                                10L,100L,incidentDate,ClaimStatus.CANCELLED));

        StepVerifier.create(operation)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void existsByIncidentDateAndStatusNot_whenIncidentDateDoesNotMatch_shouldReturnFalse() {
        Claim claim=claim("CLM-001",10L,100L,500L,ClaimStatus.SUBMITTED,false);
        claim.setIncidentDate(LocalDate.of(2026,9,15));

        Mono<Boolean> operation=claimRepository.save(claim)
                .then(claimRepository
                        .existsByCustomerIdAndPolicyIdAndIncidentDateAndStatusNot(
                                10L,100L,LocalDate.of(2026,9,16),ClaimStatus.CANCELLED));

        StepVerifier.create(operation)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void findAllByManualReviewRequiredTrue_shouldReturnOnlyManualReviewClaims() {
        Mono<List<Claim>> operation=claimRepository.saveAll(Flux.just(
                        claim("CLM-001",10L,100L,501L,ClaimStatus.SUBMITTED,true),
                        claim("CLM-002",20L,101L,502L,ClaimStatus.SUBMITTED,false),
                        claim("CLM-003",30L,102L,503L,ClaimStatus.UNDER_REVIEW,true)
                ))
                .thenMany(claimRepository.findAllByManualReviewRequiredTrue())
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->{
                    assertThat(result).hasSize(2);
                    assertThat(result).extracting(Claim::getManualReviewRequired)
                            .containsOnly(true);
                    assertThat(result).extracting(Claim::getClaimNumber)
                            .containsExactlyInAnyOrder("CLM-001","CLM-003");
                })
                .verifyComplete();
    }

    @Test
    void findAllByManualReviewRequiredTrue_whenNoClaimRequiresReview_shouldReturnEmpty() {
        Mono<List<Claim>> operation=claimRepository.saveAll(Flux.just(
                        claim("CLM-001",10L,100L,501L,ClaimStatus.SUBMITTED,false),
                        claim("CLM-002",20L,101L,502L,ClaimStatus.APPROVED,false)
                ))
                .thenMany(claimRepository.findAllByManualReviewRequiredTrue())
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void findByIdAndManualReviewRequiredTrue_whenReviewIsRequired_shouldReturnClaim() {
        Mono<Claim> operation=claimRepository
                .save(claim("CLM-001",10L,100L,500L,ClaimStatus.SUBMITTED,true))
                .flatMap(saved->
                        claimRepository.findByIdAndManualReviewRequiredTrue(saved.getId()));

        StepVerifier.create(operation)
                .assertNext(found->{
                    assertThat(found.getId()).isNotNull();
                    assertThat(found.getClaimNumber()).isEqualTo("CLM-001");
                    assertThat(found.getManualReviewRequired()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void findByIdAndManualReviewRequiredTrue_whenReviewIsNotRequired_shouldReturnEmpty() {
        Mono<Claim> operation=claimRepository
                .save(claim("CLM-001",10L,100L,500L,ClaimStatus.SUBMITTED,false))
                .flatMap(saved->
                        claimRepository.findByIdAndManualReviewRequiredTrue(saved.getId()));

        StepVerifier.create(operation).verifyComplete();
    }

    private Claim claim(String number,Long customerId,Long policyId,Long triggerEventId,
                        ClaimStatus status,boolean manualReviewRequired) {
        LocalDateTime now=LocalDateTime.now();

        return Claim.builder()
                .claimNumber(number)
                .customerId(customerId)
                .policyId(policyId)
                .policyNumber("POL-"+policyId)
                .productId(20L)
                .triggerEventId(triggerEventId)
                .triggerEventReference("TRG-"+triggerEventId)
                .triggerDefinitionId(600L)
                .perilId(700L)
                .locationZoneId(800L)
                .incidentType("FLOOD")
                .incidentDate(LocalDate.of(2026,9,15))
                .incidentLocation("Hyderabad")
                .description("Property damaged because of flooding")
                .claimedAmount(new BigDecimal("50000.00"))
                .status(status.name())
                .manualReviewRequired(manualReviewRequired)
                .submittedAt(now)
                .updatedAt(now)
                .build();
    }
}