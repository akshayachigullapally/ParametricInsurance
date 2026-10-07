package org.example.claimsservice.repository;

import org.example.claimsservice.model.Claim;
import org.example.claimsservice.model.ClaimStatus;
import org.example.claimsservice.model.ClaimTimeline;
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
class ClaimTimelineRepositoryTest {
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
    void findAllByClaimIdOrderByCreatedAtAsc_shouldReturnTimelineInAscendingOrder() {
        LocalDateTime baseTime=LocalDateTime.of(2026,9,15,12,0);

        Mono<List<ClaimTimeline>> operation=claimRepository.save(claim(
                        "CLM-001",10L,100L,500L))
                .flatMapMany(savedClaim->claimTimelineRepository.saveAll(Flux.just(
                                timeline(
                                        savedClaim,
                                        ClaimStatus.SUBMITTED.name(),
                                        ClaimStatus.UNDER_REVIEW.name(),
                                        "REVIEW_STARTED",
                                        baseTime.minusHours(1)
                                ),
                                timeline(
                                        savedClaim,
                                        null,
                                        ClaimStatus.SUBMITTED.name(),
                                        "CLAIM_SUBMITTED",
                                        baseTime.minusHours(2)
                                )
                        ))
                        .thenMany(
                                claimTimelineRepository
                                        .findAllByClaimIdOrderByCreatedAtAsc(
                                                savedClaim.getId()
                                        )
                        ))
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->{
                    assertThat(result).hasSize(2);
                    assertThat(result)
                            .extracting(ClaimTimeline::getAction)
                            .containsExactly(
                                    "CLAIM_SUBMITTED",
                                    "REVIEW_STARTED"
                            );
                    assertThat(result)
                            .extracting(ClaimTimeline::getNewStatus)
                            .containsExactly(
                                    ClaimStatus.SUBMITTED.name(),
                                    ClaimStatus.UNDER_REVIEW.name()
                            );
                    assertThat(result)
                            .extracting(ClaimTimeline::getClaimId)
                            .containsOnly(result.get(0).getClaimId());
                })
                .verifyComplete();
    }

    private Claim claim(String number,Long customerId,Long policyId,Long triggerEventId) {
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
                .status(ClaimStatus.SUBMITTED.name())
                .manualReviewRequired(false)
                .submittedAt(now)
                .updatedAt(now)
                .build();
    }

    private ClaimTimeline timeline(Claim claim,String oldStatus,String newStatus,
                                   String action,LocalDateTime createdAt) {
        return ClaimTimeline.builder()
                .claimId(claim.getId())
                .claimNumber(claim.getClaimNumber())
                .customerId(claim.getCustomerId())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .action(action)
                .actionBy(claim.getCustomerId())
                .actionByRole("CUSTOMER")
                .notes(action+" test entry")
                .createdAt(createdAt)
                .build();
    }
}