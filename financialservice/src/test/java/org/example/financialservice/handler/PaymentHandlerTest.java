package org.example.financialservice.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.dto.PaymentResponse;
import org.example.financialservice.dto.PaymentStatusResult;
import org.example.financialservice.model.PaymentMethod;
import org.example.financialservice.model.PaymentStatus;
import org.example.financialservice.service.PaymentService;
import org.example.financialservice.util.RequestValidationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PaymentHandlerTest{
    private WebTestClient webClient;
    private PaymentService paymentService;

    @BeforeEach
    void setUp(){
        paymentService=
                org.mockito.Mockito.mock(PaymentService.class);
        RequestValidationUtil validation=
                org.mockito.Mockito.mock(RequestValidationUtil.class);

        when(validation.validate(any())).thenAnswer(inv->inv.getArgument(0));

        PaymentHandler handler=new PaymentHandler(paymentService,validation);

        org.springframework.web.reactive.function.server.RouterFunction<
                org.springframework.web.reactive.function.server.ServerResponse
                > route=org.springframework.web.reactive.function.server.RouterFunctions
                .route()
                .POST("/api/financial/payments",handler::create)
                .GET("/api/financial/payments/me",handler::findMine)
                .GET("/api/financial/payments/{paymentId}",handler::findById)
                .GET("/api/financial/payments/admin/all",handler::findAll)
                .PUT("/api/financial/payments/{paymentId}/refund",
                        handler::refund)
                .GET("/internal/financial/payments/policy/{policyId}/status",
                        handler::policyStatus)
                .build();

        webClient=WebTestClient.bindToRouterFunction(route).build();
    }

    private PaymentResponse samplePayment(Long id){
        return PaymentResponse.builder()
                .id(id)
                .paymentReference("PAY-TEST")
                .customerId(5L)
                .policyId(7L)
                .policyNumber("POL-7")
                .quoteId(1L)
                .amount(new BigDecimal("10000.00"))
                .method(PaymentMethod.MOCK_CARD)
                .status(PaymentStatus.PENDING)
                .attemptNumber(1)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createReturns201(){
        when(paymentService.create(any()))
                .thenReturn(Mono.just(samplePayment(1L)));

        webClient.post()
                .uri("/api/financial/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"policyId\":7,\"method\":\"MOCK_CARD\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.paymentReference").isEqualTo("PAY-TEST")
                .jsonPath("$.amount").isEqualTo(10000.00);
    }

    @Test
    void findMineReturnsList(){
        when(paymentService.findMine())
                .thenReturn(Flux.just(samplePayment(1L),samplePayment(2L)));

        webClient.get()
                .uri("/api/financial/payments/me")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PaymentResponse.class).hasSize(2);
    }

    @Test
    void findByIdReturnsPayment(){
        when(paymentService.findById(1L))
                .thenReturn(Mono.just(samplePayment(1L)));

        webClient.get()
                .uri("/api/financial/payments/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1);
    }

    @Test
    void findAllReturnsList(){
        when(paymentService.findAll())
                .thenReturn(Flux.just(samplePayment(1L)));

        webClient.get()
                .uri("/api/financial/payments/admin/all")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PaymentResponse.class).hasSize(1);
    }

    @Test
    void refundReturnsRefundedPayment(){
        PaymentResponse refunded=samplePayment(1L);
        refunded.setStatus(PaymentStatus.REFUNDED);

        when(paymentService.refund(1L))
                .thenReturn(Mono.just(refunded));

        webClient.put()
                .uri("/api/financial/payments/1/refund")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REFUNDED");
    }

    @Test
    void policyStatusReturnsResult(){
        when(paymentService.policyStatus(7L))
                .thenReturn(Mono.just(PaymentStatusResult.builder()
                        .policyId(7L)
                        .latestStatus(PaymentStatus.SUCCESS)
                        .successful(true)
                        .build()));

        webClient.get()
                .uri("/internal/financial/payments/policy/7/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.successful").isEqualTo(true);
    }
}
