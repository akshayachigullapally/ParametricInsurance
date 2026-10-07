package org.example.claimsservice.handler;

import org.example.claimsservice.dto.*;
import org.example.claimsservice.exception.ConflictException;
import org.example.claimsservice.exception.NotFoundException;
import org.example.claimsservice.exception.ValidationException;
import org.example.claimsservice.service.ClaimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimHandlerTest {

    @Mock
    private ClaimService claimService;

    @Mock
    private ServerRequest serverRequest;

    private Validator validator;
    private ClaimHandler handler;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        handler = new ClaimHandler(claimService, validator);
    }

    @SuppressWarnings("unchecked")
    private void mockPrincipal(long userId) {
        when(serverRequest.principal()).thenReturn((Mono) Mono.just(
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList())
        ));
    }

    @Test
    void createClaim_whenValid_shouldReturnCreated() {
        ClaimResponse response = claimResponse(1L, "SUBMITTED");
        when(claimService.createClaim(eq(1L), any(CreateClaimRequest.class)))
                .thenReturn(Mono.just(response));

        CreateClaimRequest body = CreateClaimRequest.builder()
                .policyId(100L)
                .incidentType("FLOOD")
                .incidentDate(LocalDate.now().minusDays(1))
                .incidentLocation("Hyderabad")
                .description("Property damaged because of flooding")
                .claimedAmount(new BigDecimal("50000"))
                .build();

        mockPrincipal(1L);
        when(serverRequest.bodyToMono(CreateClaimRequest.class))
                .thenReturn(Mono.just(body));

        StepVerifier.create(handler.createClaim(serverRequest))
                .assertNext(response1 -> {
                    assertThat(response1.statusCode()).isEqualTo(HttpStatus.CREATED);
                })
                .verifyComplete();

        verify(claimService).createClaim(eq(1L), any(CreateClaimRequest.class));
    }

    @Test
    void getMyClaim_whenNotExists_shouldReturnError() {
        when(claimService.getMyClaim(1L, 99L))
                .thenReturn(Mono.error(new NotFoundException("Claim not found")));
        when(serverRequest.pathVariable("claimId")).thenReturn("99");
        mockPrincipal(1L);

        StepVerifier.create(handler.getMyClaim(serverRequest))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void cancelClaim_whenNotSubmitted_shouldReturnConflict() {
        when(claimService.cancelClaim(1L, 1L))
                .thenReturn(Mono.error(
                        new ConflictException("Only submitted claims can be cancelled")
                ));
        when(serverRequest.pathVariable("claimId")).thenReturn("1");
        mockPrincipal(1L);

        StepVerifier.create(handler.cancelClaim(serverRequest))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void getSubmittedClaims_shouldReturnClaims() {
        ClaimResponse r1 = claimResponse(1L, "SUBMITTED");

        when(claimService.getSubmittedClaims())
                .thenReturn(Flux.just(r1));

        StepVerifier.create(handler.getSubmittedClaims(serverRequest))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void approveClaim_whenValid_shouldReturnOk() {
        ClaimResponse response = claimResponse(1L, "APPROVED");

        when(claimService.approveClaim(eq(1L), eq(1L), any(ApproveClaimRequest.class)))
                .thenReturn(Mono.just(response));
        when(claimService.requestPayout(1L)).thenReturn(Mono.empty());
        when(claimService.getClaim(1L)).thenReturn(Mono.just(claimResponse(1L, "SETTLED")));
        when(serverRequest.pathVariable("claimId")).thenReturn("1");
        mockPrincipal(1L);
        when(serverRequest.bodyToMono(ApproveClaimRequest.class))
                .thenReturn(Mono.just(ApproveClaimRequest.builder()
                        .approvedAmount(new BigDecimal("40000"))
                        .build()));

        StepVerifier.create(handler.approveClaim(serverRequest))
                .assertNext(resp -> {
                    assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();

        // Approval pays out automatically, so the latest claim (settled) is returned.
        org.mockito.Mockito.verify(claimService).getClaim(1L);
    }

    @Test
    void rejectClaim_whenBlankReason_shouldReturnValidationError() {
        when(serverRequest.pathVariable("claimId")).thenReturn("1");
        mockPrincipal(1L);
        when(serverRequest.bodyToMono(RejectClaimRequest.class))
                .thenReturn(Mono.just(RejectClaimRequest.builder()
                        .reason("  ")
                        .build()));

        StepVerifier.create(handler.rejectClaim(serverRequest))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void getAllClaims_shouldReturnClaims() {
        ClaimResponse r1 = claimResponse(1L, "SUBMITTED");
        ClaimResponse r2 = claimResponse(2L, "APPROVED");

        when(claimService.getAllClaims(null, null))
                .thenReturn(Flux.just(r1, r2));
        when(serverRequest.queryParam("status")).thenReturn(java.util.Optional.empty());

        StepVerifier.create(handler.getAllClaims(serverRequest))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void startReview_whenClaimNotFound_shouldReturnError() {
        when(claimService.startReview(1L, 99L))
                .thenReturn(Mono.error(new NotFoundException("Claim not found")));
        when(serverRequest.pathVariable("claimId")).thenReturn("99");
        mockPrincipal(1L);

        StepVerifier.create(handler.startReview(serverRequest))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void getClaim_whenNegativeId_shouldReturnValidationError() {
        when(serverRequest.pathVariable("claimId")).thenReturn("-1");

        StepVerifier.create(handler.getClaim(serverRequest))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void getManualClaims_shouldReturnManualQueueClaims() {
        ClaimResponse r1 = claimResponse(1L, "SUBMITTED");
        r1.setClaimSource("MANUAL_EXCEPTION");
        r1.setManualReviewRequired(true);

        when(claimService.getManualClaims())
                .thenReturn(Flux.just(r1));

        StepVerifier.create(handler.getManualClaims(serverRequest))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void getManualClaim_whenInvalidId_shouldReturnValidationError() {
        when(serverRequest.pathVariable("claimId")).thenReturn("abc");

        StepVerifier.create(handler.getManualClaim(serverRequest))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void getMyClaims_whenEmpty_shouldReturnEmpty() {
        when(claimService.getMyClaims(1L, null))
                .thenReturn(Flux.empty());
        mockPrincipal(1L);
        when(serverRequest.queryParam("status")).thenReturn(java.util.Optional.empty());

        StepVerifier.create(handler.getMyClaims(serverRequest))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void settleClaim_whenConflict_shouldReturnError() {
        when(claimService.settleClaim(eq(1L), eq(1L), any(SettleClaimRequest.class)))
                .thenReturn(Mono.error(
                        new ConflictException("Only approved claims can be settled")
                ));
        when(serverRequest.pathVariable("claimId")).thenReturn("1");
        mockPrincipal(1L);
        when(serverRequest.bodyToMono(SettleClaimRequest.class))
                .thenReturn(Mono.just(SettleClaimRequest.builder()
                        .notes("Payment processed")
                        .build()));

        StepVerifier.create(handler.settleClaim(serverRequest))
                .expectError(ConflictException.class)
                .verify();
    }

    private ClaimResponse claimResponse(Long id, String status) {
        return ClaimResponse.builder()
                .id(id)
                .claimNumber("CLM-2026-TEST000" + id)
                .customerId(1L)
                .policyId(100L)
                .policyNumber("POL-100")
                .productId(20L)
                .triggerEventId(500L)
                .triggerEventReference("TRG-500")
                .triggerDefinitionId(600L)
                .perilId(700L)
                .locationZoneId(800L)
                .incidentType("FLOOD")
                .incidentDate(LocalDate.now().minusDays(1))
                .incidentLocation("Hyderabad")
                .description("Property damaged because of flooding")
                .claimedAmount(new BigDecimal("50000"))
                .status(status)
                .submittedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
