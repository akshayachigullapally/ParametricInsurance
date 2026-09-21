package org.example.notificationservice.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalErrorHandlerTest {

    @Mock
    private ObjectMapper mapper;

    @InjectMocks
    private GlobalErrorHandler handler;

    private MockServerWebExchange createExchange(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        return MockServerWebExchange.from(request);
    }

    @Test
    void handle_notFoundException_returns404() {
        MockServerWebExchange exchange = createExchange("/api/notifications/99");
        NotFoundException ex = new NotFoundException("Notification not found");

        var result = handler.handle(exchange, ex);

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handle_forbiddenException_returns403() {
        MockServerWebExchange exchange = createExchange("/api/notifications/1");
        ForbiddenException ex = new ForbiddenException("Access denied");

        var result = handler.handle(exchange, ex);

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handle_unauthorizedException_returns401() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me");
        UnauthorizedException ex = new UnauthorizedException("Authentication required");

        var result = handler.handle(exchange, ex);

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handle_validationException_returns400() {
        MockServerWebExchange exchange = createExchange("/internal/notifications");
        ValidationException ex = new ValidationException("Title must not be blank");

        var result = handler.handle(exchange, ex);

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handle_unexpectedException_returns500() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me");
        RuntimeException ex = new RuntimeException("Something went wrong");

        var result = handler.handle(exchange, ex);

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void handle_unexpectedException_messageIsSanitized() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me");
        RuntimeException ex = new RuntimeException("Internal DB password leaked");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void handle_webExchangeBindException_returns400() {
        MockServerWebExchange exchange = createExchange("/internal/notifications");
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "title", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));
        MethodParameter parameter = mock(MethodParameter.class);
        WebExchangeBindException ex = new WebExchangeBindException(parameter, bindingResult);

        var result = handler.handle(exchange, ex);

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handle_responseContentTypeIsJson() {
        MockServerWebExchange exchange = createExchange("/api/notifications/1");
        NotFoundException ex = new NotFoundException("Not found");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }
}
