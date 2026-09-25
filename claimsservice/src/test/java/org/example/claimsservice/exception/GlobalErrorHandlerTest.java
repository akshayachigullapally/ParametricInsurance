package org.example.claimsservice.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalErrorHandlerTest{
    private final GlobalErrorHandler handler=new GlobalErrorHandler(JsonMapper.builder().build());

    static Stream<Arguments> exceptions(){
        return Stream.of(
                Arguments.of(new NotFoundException("Claim not found"),HttpStatus.NOT_FOUND),
                Arguments.of(new ConflictException("Only submitted claims can be cancelled"),HttpStatus.CONFLICT),
                Arguments.of(new ForbiddenException("Customer does not own policy"),HttpStatus.FORBIDDEN),
                Arguments.of(new ValidationException("Approved amount is required"),HttpStatus.BAD_REQUEST),
                Arguments.of(new IllegalArgumentException("bad input"),HttpStatus.BAD_REQUEST),
                Arguments.of(new DependentServiceException("Product service unavailable"),HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(new IllegalStateException("boom"),HttpStatus.INTERNAL_SERVER_ERROR)
        );
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void handle_shouldMapExceptionToStatus(RuntimeException exception,HttpStatus expected){
        MockServerWebExchange exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/claims/1"));

        StepVerifier.create(handler.handle(exchange,exception)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expected);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void handle_validationException_shouldReturnEveryError(){
        MockServerWebExchange exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/claims"));

        StepVerifier.create(handler.handle(exchange,
                        new ValidationException(List.of("Policy ID is required","Claimed amount is required"))))
                .verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body->assertThat(body)
                        .contains("Policy ID is required")
                        .contains("Claimed amount is required")
                        .contains("/api/claims"))
                .verifyComplete();
    }

    @Test
    void handle_exceptionWithoutMessage_shouldUseFallbackMessage(){
        MockServerWebExchange exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/claims/me"));

        StepVerifier.create(handler.handle(exchange,new RuntimeException())).verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body->assertThat(body).contains("Unexpected error"))
                .verifyComplete();
    }

    @Test
    void handle_whenSerializationFails_shouldStillSetStatus(){
        ObjectMapper failing=mock(ObjectMapper.class);
        when(failing.writeValueAsBytes(any())).thenThrow(new RuntimeException("cannot serialize"));
        MockServerWebExchange exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/claims/1"));

        StepVerifier.create(new GlobalErrorHandler(failing).handle(exchange,new NotFoundException("missing")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
