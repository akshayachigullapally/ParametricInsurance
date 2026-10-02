package org.example.financialservice.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.example.financialservice.dto.PayoutResponse;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.service.PayoutService;
import org.example.financialservice.util.RequestValidationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PayoutHandlerTest{
    private WebTestClient webClient;
    private PayoutService payoutService;

    @BeforeEach
    void setUp(){
        payoutService=
                org.mockito.Mockito.mock(PayoutService.class);
        RequestValidationUtil validation=
                org.mockito.Mockito.mock(RequestValidationUtil.class);

        when(validation.validate(any())).thenAnswer(inv->inv.getArgument(0));

        PayoutHandler handler=new PayoutHandler(payoutService,validation);

        org.springframework.web.reactive.function.server.RouterFunction<
                org.springframework.web.reactive.function.server.ServerResponse
                > route=org.springframework.web.reactive.function.server.RouterFunctions
                .route()
                .POST("/api/financial/payouts",handler::create)
                .POST("/internal/financial/payouts",handler::createInternal)
                .GET("/api/financial/payouts/me",handler::findMine)
                .GET("/api/financial/payouts/{payoutId}",handler::findById)
                .GET("/api/financial/payouts/admin/all",handler::findAll)
                .GET("/internal/financial/payouts/claim/{claimId}",
                        handler::findByClaim)
                .PUT("/api/financial/payouts/{payoutId}/retry",handler::retry)
                .build();

        org.springframework.web.reactive.function.server.RouterFunction<
                org.springframework.web.reactive.function.server.ServerResponse
                > securedRoute=route.filter((request,next)->{
                    UsernamePasswordAuthenticationToken auth=
                            new UsernamePasswordAuthenticationToken(
                                    "1",null,
                                    List.of(new SimpleGrantedAuthority(
                                            "ROLE_CLAIMS_OFFICER")));
                    return next.handle(request)
                            .contextWrite(
                                    ReactiveSecurityContextHolder
                                            .withAuthentication(auth));
                });

        webClient=WebTestClient.bindToRouterFunction(securedRoute)
                .build();
    }

    private PayoutResponse samplePayout(Long id){
        return PayoutResponse.builder()
                .id(id)
                .payoutReference("PO-TEST")
                .customerId(5L)
                .claimId(10L)
                .claimNumber("CLM-10")
                .policyId(7L)
                .triggerEventId(3L)
                .amount(new BigDecimal("5000.00"))
                .status(PayoutStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createReturns201(){
        when(payoutService.create(any()))
                .thenReturn(Mono.just(samplePayout(1L)));

        webClient.post()
                .uri("/api/financial/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"claimId\":10}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.payoutReference").isEqualTo("PO-TEST")
                .jsonPath("$.amount").isEqualTo(5000.00);
    }

    @Test
    void createInternalReturns201(){
        when(payoutService.create(any()))
                .thenReturn(Mono.just(samplePayout(1L)));

        webClient.post()
                .uri("/internal/financial/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"claimId\":10}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void findMineReturnsList(){
        when(payoutService.findMine())
                .thenReturn(Flux.just(samplePayout(1L),samplePayout(2L)));

        webClient.get()
                .uri("/api/financial/payouts/me")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PayoutResponse.class).hasSize(2);
    }

    @Test
    void findByIdReturnsPayout(){
        when(payoutService.findById(1L))
                .thenReturn(Mono.just(samplePayout(1L)));

        webClient.get()
                .uri("/api/financial/payouts/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1);
    }

    @Test
    void findAllReturnsList(){
        when(payoutService.findAll())
                .thenReturn(Flux.just(samplePayout(1L)));

        webClient.get()
                .uri("/api/financial/payouts/admin/all")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PayoutResponse.class).hasSize(1);
    }

    @Test
    void findByClaimReturnsList(){
        when(payoutService.findByClaim(10L))
                .thenReturn(Flux.just(samplePayout(1L)));

        webClient.get()
                .uri("/internal/financial/payouts/claim/10")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PayoutResponse.class).hasSize(1);
    }

    @Test
    void retryReturnsProcessing(){
        PayoutResponse retry=samplePayout(1L);
        retry.setStatus(PayoutStatus.PROCESSING);
        retry.setRetryCount(1);

        when(payoutService.retry(anyLong(),any()))
                .thenReturn(Mono.just(retry));

        webClient.put()
                .uri("/api/financial/payouts/1/retry")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.retryCount").isEqualTo(1);
    }
}
