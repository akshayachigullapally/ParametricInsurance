package org.example.authservice.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalErrorHandlerTest{
    private final GlobalErrorHandler handler=new GlobalErrorHandler(new ObjectMapper().findAndRegisterModules());

    static Stream<Arguments> exceptions(){
        return Stream.of(
                Arguments.of(new ConflictException("Email is already registered"),HttpStatus.CONFLICT),
                Arguments.of(new AuthenticationException("Invalid email or password"),HttpStatus.UNAUTHORIZED),
                Arguments.of(new NotFoundException("User not found"),HttpStatus.NOT_FOUND),
                Arguments.of(new ValidationException("Email is required"),HttpStatus.BAD_REQUEST),
                Arguments.of(new ForbiddenException("Admin access required"),HttpStatus.FORBIDDEN),
                Arguments.of(new IllegalStateException("boom"),HttpStatus.INTERNAL_SERVER_ERROR)
        );
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void handle_shouldMapExceptionToStatus(RuntimeException exception,HttpStatus expected){
        MockServerWebExchange exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me"));

        StepVerifier.create(handler.handle(exchange,exception)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expected);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void handle_validationException_shouldReturnEveryError(){
        MockServerWebExchange exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/register"));

        StepVerifier.create(handler.handle(exchange,
                        new ValidationException(List.of("Email is required","Password is required"))))
                .verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body->assertThat(body)
                        .contains("Email is required")
                        .contains("Password is required")
                        .contains("/api/auth/register"))
                .verifyComplete();
    }

    @Test
    void handle_exceptionWithoutMessage_shouldUseFallbackMessage(){
        MockServerWebExchange exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));

        StepVerifier.create(handler.handle(exchange,new RuntimeException())).verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body->assertThat(body).contains("Unexpected error"))
                .verifyComplete();
    }

    @Test
    void handle_whenSerializationFails_shouldStillCompleteWithStatus() throws Exception{
        ObjectMapper failing=mock(ObjectMapper.class);
        when(failing.writeValueAsBytes(any())).thenThrow(new RuntimeException("cannot serialize"));
        GlobalErrorHandler failingHandler=new GlobalErrorHandler(failing);
        MockServerWebExchange exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));

        StepVerifier.create(failingHandler.handle(exchange,new NotFoundException("missing"))).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
