package org.example.productservice.handler;

import org.example.productservice.config.SecurityConfig;
import org.example.productservice.dto.*;
import org.example.productservice.exception.*;
import org.example.productservice.filter.GatewayAuthenticationFilter;
import org.example.productservice.router.PolicyRouter;
import org.example.productservice.service.PolicyService;
import org.example.productservice.util.GatewayHeaders;
import org.example.productservice.util.SecurityUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers=PolicyHandler.class,excludeAutoConfiguration=org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration.class)
@Import({PolicyRouter.class,SecurityUtil.class,SecurityConfig.class,GatewayAuthenticationFilter.class})
class PolicyHandlerTest{
    @Autowired private WebTestClient webTestClient;
    @MockitoBean private PolicyService policyService;
    @MockitoBean private org.example.productservice.service.ZoneUnderwritingService zoneUnderwritingService;

    private WebTestClient authClient(String role,String id){
        return webTestClient.mutate()
                .defaultHeader(GatewayHeaders.USER_ROLE,role)
                .defaultHeader(GatewayHeaders.USER_ID,id)
                .defaultHeader(GatewayHeaders.USER_EMAIL,
                        ("CUSTOMER".equals(role)?"customer":"staff")+"@climatesure.test")
                .build();
    }

    private PolicyResponse buildResponse(Long id){
        return PolicyResponse.builder()
                .id(id)
                .policyNumber("POL-2026-ABC12345")
                .customerId(1L)
                .productId(10L)
                .productCode("WEATHER-01")
                .productName("Weather Shield")
                .locationZoneId(7L)
                .sumInsured(new BigDecimal("120000"))
                .premiumAmount(new BigDecimal("1200.00"))
                .termMonths(6)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(6))
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private CreatePolicyRequest buildCreateRequest(){
        return CreatePolicyRequest.builder()
                .productId(10L)
                .locationZoneId(7L)
                .sumInsured(new BigDecimal("120000"))
                .termMonths(6)
                .build();
    }

    @Test
    void createPolicy_shouldReturn201(){
        PolicyResponse response=buildResponse(100L);
        when(policyService.createPolicy(anyLong(),any(CreatePolicyRequest.class)))
                .thenReturn(Mono.just(response));
        authClient("CUSTOMER","1").post().uri("/api/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest())
                .exchange().expectStatus().isCreated()
                .expectBody(PolicyResponse.class)
                .value(resp->assertThat(resp.getId()).isEqualTo(100L));
    }

    @Test
    void getMyPolicies_shouldReturnPolicies(){
        when(policyService.getMyPolicies(eq(1L),isNull()))
                .thenReturn(Flux.just(buildResponse(100L),buildResponse(101L)));
        authClient("CUSTOMER","1").get().uri("/api/policies/me")
                .exchange().expectStatus().isOk()
                .expectBodyList(PolicyResponse.class).hasSize(2);
    }

    @Test
    void getMyPolicy_shouldReturnPolicy(){
        when(policyService.getMyPolicy(eq(1L),eq(100L)))
                .thenReturn(Mono.just(buildResponse(100L)));
        authClient("CUSTOMER","1").get().uri("/api/policies/{policyId}",100L)
                .exchange().expectStatus().isOk();
    }

    @Test
    void cancelPolicy_shouldReturn200(){
        MessageResponse response=MessageResponse.builder().message("Policy cancelled successfully").build();
        when(policyService.cancelPolicy(eq(1L),eq(100L))).thenReturn(Mono.just(response));
        authClient("CUSTOMER","1").put().uri("/api/policies/{policyId}/cancel",100L)
                .exchange().expectStatus().isOk();
    }

    @Test
    void getPendingPolicies_shouldReturnPendingPolicies(){
        when(policyService.getPendingPolicies()).thenReturn(Flux.just(buildResponse(100L)));
        authClient("UNDERWRITER","1").get().uri("/api/policies/review/pending")
                .exchange().expectStatus().isOk()
                .expectBodyList(PolicyResponse.class).hasSize(1);
    }

    @Test
    void getAllPolicies_withStatusFilter_shouldReturnFiltered(){
        when(policyService.getAllPolicies(eq("ACTIVE"),isNull())).thenReturn(Flux.just(buildResponse(100L)));
        authClient("ADMIN","1").get()
                .uri(uriBuilder->uriBuilder.path("/api/policies/admin/all").queryParam("status","ACTIVE").build())
                .exchange().expectStatus().isOk()
                .expectBodyList(PolicyResponse.class).hasSize(1);
    }

    @Test
    void getPolicy_shouldReturnPolicy(){
        when(policyService.getPolicy(100L)).thenReturn(Mono.just(buildResponse(100L)));
        authClient("ADMIN","1").get().uri("/api/policies/admin/{policyId}",100L)
                .exchange().expectStatus().isOk();
    }

    @Test
    void approvePolicy_shouldReturn200(){
        PolicyResponse response=buildResponse(100L);
        response.setStatus("ACTIVE");
        when(policyService.approvePolicy(eq(55L),eq(100L))).thenReturn(Mono.just(response));
        authClient("UNDERWRITER","55").put().uri("/api/policies/{policyId}/approve",100L)
                .exchange().expectStatus().isOk();
    }

    @Test
    void rejectPolicy_shouldReturn200(){
        PolicyResponse response=buildResponse(100L);
        response.setStatus("REJECTED");
        when(policyService.rejectPolicy(eq(55L),eq(100L),any(PolicyDecisionRequest.class)))
                .thenReturn(Mono.just(response));
        authClient("UNDERWRITER","55").put().uri("/api/policies/{policyId}/reject",100L)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(PolicyDecisionRequest.builder().reason("Risk too high").build())
                .exchange().expectStatus().isOk();
    }

    @Test
    void rejectPolicy_whenNotPending_shouldReturn409(){
        when(policyService.rejectPolicy(eq(55L),eq(100L),any(PolicyDecisionRequest.class)))
                .thenReturn(Mono.error(new ConflictException("Only pending policies can be rejected")));
        authClient("UNDERWRITER","55").put().uri("/api/policies/{policyId}/reject",100L)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(PolicyDecisionRequest.builder().reason("Risk too high").build())
                .exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void getInternalPolicy_shouldReturnPolicy(){
        InternalPolicyResponse response=InternalPolicyResponse.builder()
                .id(100L).policyNumber("POL-2026-ABC12345").customerId(1L)
                .productId(10L).productCode("WEATHER-01").productName("Weather Shield")
                .locationZoneId(7L).sumInsured(new BigDecimal("120000"))
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(6))
                .status("PENDING").build();
        when(policyService.getInternalPolicy(100L)).thenReturn(Mono.just(response));
        webTestClient.get().uri("/internal/policies/{policyId}",100L)
                .exchange().expectStatus().isOk();
    }

    @Test
    void getInternalPolicy_whenNotFound_shouldReturn404(){
        when(policyService.getInternalPolicy(99L)).thenReturn(Mono.error(new NotFoundException("Policy not found")));
        webTestClient.get().uri("/internal/policies/{policyId}",99L)
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void getInternalPolicy_whenInvalidPolicyId_shouldReturn400(){
        webTestClient.get().uri("/internal/policies/{policyId}","abc")
                .exchange().expectStatus().is4xxClientError();
    }

    @Test
    void createPolicy_whenValidationFailsWithMultipleErrors_shouldReturn400(){
        when(policyService.createPolicy(anyLong(),any(CreatePolicyRequest.class)))
                .thenReturn(Mono.error(new ValidationException(List.of("Sum insured is required","Policy term is required"))));
        authClient("CUSTOMER","1").post().uri("/api/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest())
                .exchange().expectStatus().is4xxClientError();
    }

    @Test
    void getPolicyHistory_shouldReturnHistory(){
        PolicyStatusHistoryResponse history=PolicyStatusHistoryResponse.builder()
                .id(1L).policyId(100L).oldStatus("PENDING").newStatus("ACTIVE")
                .changedBy(55L).changedAt(LocalDateTime.now()).build();
        when(policyService.getPolicyHistory(eq(100L))).thenReturn(Flux.just(history));
        authClient("CUSTOMER","1").get().uri("/api/policies/{policyId}/history",100L)
                .exchange().expectStatus().isOk()
                .expectBodyList(PolicyStatusHistoryResponse.class).hasSize(1);
    }

    @Test
    void getPolicySnapshot_shouldReturnSnapshot(){
        PolicyTriggerSnapshotResponse snapshot=PolicyTriggerSnapshotResponse.builder()
                .id(1L).policyId(100L).perilCode("HAIL").triggerMetric("hail_size")
                .triggerOperator("GTE").triggerThreshold(new BigDecimal("25.0")).build();
        when(policyService.getPolicySnapshot(eq(100L))).thenReturn(Mono.just(snapshot));
        authClient("CUSTOMER","1").get().uri("/api/policies/{policyId}/snapshot",100L)
                .exchange().expectStatus().isOk();
    }
}