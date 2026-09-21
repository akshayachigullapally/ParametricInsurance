package org.example.financialservice.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.dto.QuoteResponse;
import org.example.financialservice.model.QuoteStatus;
import org.example.financialservice.service.QuoteService;
import org.example.financialservice.util.RequestValidationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class QuoteHandlerTest{
    private WebTestClient webClient;
    private QuoteService quoteService;

    @BeforeEach
    void setUp(){
        quoteService=org.mockito.Mockito.mock(QuoteService.class);
        RequestValidationUtil validation=
                org.mockito.Mockito.mock(RequestValidationUtil.class);

        when(validation.validate(any())).thenAnswer(inv->inv.getArgument(0));

        org.example.financialservice.handler.QuoteHandler handler=
                new org.example.financialservice.handler.QuoteHandler(
                        quoteService,
                        validation
                );

        org.springframework.web.reactive.function.server.RouterFunction<
                org.springframework.web.reactive.function.server.ServerResponse
                > route=org.springframework.web.reactive.function.server.RouterFunctions
                .route()
                .GET("/api/financial/quotes/me",handler::findMine)
                .GET("/api/financial/quotes/{quoteId}",handler::findById)
                .POST("/api/financial/quotes",handler::create)
                .PUT("/api/financial/quotes/{quoteId}/accept",handler::accept)
                .PUT("/api/financial/quotes/{quoteId}/cancel",handler::cancel)
                .POST("/internal/financial/quotes/{quoteId}/validate",
                        handler::validateInternal)
                .PUT("/internal/financial/quotes/{quoteId}/consume",
                        handler::consumeInternal)
                .build();

        webClient=WebTestClient.bindToRouterFunction(route).build();
    }

    private QuoteResponse sampleQuote(Long id){
        return QuoteResponse.builder()
                .id(id)
                .quoteReference("QTE-TEST")
                .customerId(5L)
                .productId(1L)
                .locationZoneId(2L)
                .sumInsured(new BigDecimal("100000.00"))
                .termMonths(12)
                .annualRate(new BigDecimal("0.100000"))
                .termFactor(new BigDecimal("1.0000"))
                .locationFactor(new BigDecimal("1.0000"))
                .premiumAmount(new BigDecimal("10000.00"))
                .status(QuoteStatus.CREATED)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createReturns201(){
        when(quoteService.create(any()))
                .thenReturn(Mono.just(sampleQuote(1L)));

        webClient.post()
                .uri("/api/financial/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"productId\":1,"+
                        "\"locationZoneId\":2,"+
                        "\"sumInsured\":100000.00,"+
                        "\"termMonths\":12}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.quoteReference").isEqualTo("QTE-TEST")
                .jsonPath("$.premiumAmount").isEqualTo(10000.00);
    }

    @Test
    void findMineReturnsList(){
        when(quoteService.findMine())
                .thenReturn(Flux.just(sampleQuote(1L),sampleQuote(2L)));

        webClient.get()
                .uri("/api/financial/quotes/me")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(QuoteResponse.class).hasSize(2);
    }

    @Test
    void findByIdReturnsQuote(){
        when(quoteService.findMine(1L))
                .thenReturn(Mono.just(sampleQuote(1L)));

        webClient.get()
                .uri("/api/financial/quotes/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1);
    }

    @Test
    void acceptReturnsAcceptedQuote(){
        QuoteResponse accepted=sampleQuote(1L);
        accepted.setStatus(QuoteStatus.ACCEPTED);

        when(quoteService.accept(1L))
                .thenReturn(Mono.just(accepted));

        webClient.put()
                .uri("/api/financial/quotes/1/accept")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED");
    }

    @Test
    void cancelReturnsCancelledQuote(){
        QuoteResponse cancelled=sampleQuote(1L);
        cancelled.setStatus(QuoteStatus.CANCELLED);

        when(quoteService.cancel(1L))
                .thenReturn(Mono.just(cancelled));

        webClient.put()
                .uri("/api/financial/quotes/1/cancel")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }
}
