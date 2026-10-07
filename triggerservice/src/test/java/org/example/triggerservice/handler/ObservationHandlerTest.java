package org.example.triggerservice.handler;

import org.example.triggerservice.dto.ObservationResponse;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.service.ObservationService;
import org.example.triggerservice.util.RequestValidationUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObservationHandlerTest {

    @Mock
    private ObservationService observationService;

    @Mock
    private RequestValidationUtil validationUtil;

    @InjectMocks
    private ObservationHandler observationHandler;

    private ServerRequest createRequest(String method, String path) {
        MockServerHttpRequest httpRequest = MockServerHttpRequest.method(
                org.springframework.http.HttpMethod.valueOf(method), path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
        return ServerRequest.create(exchange, List.of());
    }

    // ServerRequest.create() does not perform route pattern matching, so it cannot
    // resolve path variables. Use MockServerRequest (Spring's functional-handler test
    // fixture) whenever the handler under test reads one via request.pathVariable(...).
    private ServerRequest createRequest(String method, String path, Map<String, String> pathVariables) {
        return MockServerRequest.builder()
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(URI.create(path))
                .pathVariables(pathVariables)
                .build();
    }

    @Test
    void getById_shouldReturnValidationForInvalidId() {
        ServerRequest request = createRequest("GET", "/api/triggers/observations/abc", Map.of("observationId", "abc"));

        StepVerifier.create(
                        observationHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .expectError(ValidationException.class)
                .verify();
    }
}
