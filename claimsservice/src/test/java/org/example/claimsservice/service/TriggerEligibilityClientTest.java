package org.example.claimsservice.service;

import org.example.claimsservice.exception.DependentServiceException;
import org.example.claimsservice.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerEligibilityClientTest {

    private TriggerEligibilityClient triggerEligibilityClient;
    private AtomicReference<ClientRequest> capturedRequest;
    private ClientResponse clientResponse;

    @BeforeEach
    void setUp() {
        capturedRequest=new AtomicReference<>();

        ExchangeFunction exchangeFunction=request->{
            capturedRequest.set(request);
            return Mono.just(clientResponse);
        };

        WebClient.Builder webClientBuilder=WebClient.builder()
                .exchangeFunction(exchangeFunction);

        triggerEligibilityClient=new TriggerEligibilityClient(
                webClientBuilder
        );

        ReflectionTestUtils.setField(
                triggerEligibilityClient,
                "internalKey",
                "test-internal-key"
        );
    }

    @Test
    void getEligibility_whenResponseIsNotFound_shouldReturnNotFoundException() {
        clientResponse=ClientResponse.create(HttpStatus.NOT_FOUND)
                .build();

        StepVerifier.create(
                        triggerEligibilityClient.getEligibility(999L)
                )
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Policy trigger eligibility was not found"
                    );
                })
                .verify();
    }

    @Test
    void getEligibility_whenResponseIsBadRequest_shouldReturnDependentServiceException() {
        clientResponse=ClientResponse.create(HttpStatus.BAD_REQUEST)
                .build();

        StepVerifier.create(
                        triggerEligibilityClient.getEligibility(100L)
                )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(DependentServiceException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Trigger eligibility validation failed"
                    );
                })
                .verify();
    }

    @Test
    void getEligibility_whenServiceReturnsServerError_shouldReturnDependentServiceException() {
        clientResponse=ClientResponse
                .create(HttpStatus.SERVICE_UNAVAILABLE)
                .build();

        StepVerifier.create(
                        triggerEligibilityClient.getEligibility(100L)
                )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(DependentServiceException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Trigger service is currently unavailable"
                    );
                })
                .verify();
    }
}