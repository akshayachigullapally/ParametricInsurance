package org.example.financialservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class GlobalErrorHandlerTest{
    private GlobalErrorHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp(){
        objectMapper=new ObjectMapper();
        handler=new GlobalErrorHandler(objectMapper);
    }

    private MockServerWebExchange exchange(String path){
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build());
    }

    @Test
    void notFoundExceptionReturns404(){
        MockServerWebExchange ex=exchange("/api/test");

        StepVerifier.create(handler.handle(ex,
                new NotFoundException("Quote not found")))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void conflictExceptionReturns409(){
        MockServerWebExchange ex=exchange("/api/test");

        StepVerifier.create(handler.handle(ex,
                new ConflictException("Already exists")))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void forbiddenExceptionReturns403(){
        MockServerWebExchange ex=exchange("/api/test");

        StepVerifier.create(handler.handle(ex,
                new ForbiddenException("Access denied")))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unauthorizedExceptionReturns401(){
        MockServerWebExchange ex=exchange("/api/test");

        StepVerifier.create(handler.handle(ex,
                new UnauthorizedException("Not authenticated")))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validationExceptionReturns400(){
        MockServerWebExchange ex=exchange("/api/test");

        StepVerifier.create(handler.handle(ex,
                new ValidationException("Invalid input")))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void genericExceptionReturns500(){
        MockServerWebExchange ex=exchange("/api/test");

        StepVerifier.create(handler.handle(ex,
                new RuntimeException("Something broke")))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
