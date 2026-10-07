package org.example.triggerservice.handler;

import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.TriggerEvaluationService;
import org.example.triggerservice.util.RequestValidationUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;
import java.util.Map;


@ExtendWith(MockitoExtension.class)
class TriggerEvaluationHandlerTest {

    @Mock
    private TriggerEvaluationService triggerEvaluationService;

    @Mock
    private RequestValidationUtil validationUtil;

    @InjectMocks
    private TriggerEvaluationHandler triggerEvaluationHandler;

    private ServerRequest createRequest(String method, String path) {
        MockServerHttpRequest httpRequest = MockServerHttpRequest.method(
                org.springframework.http.HttpMethod.valueOf(method), path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
        return ServerRequest.create(exchange, List.of());
    }

    private ServerRequest createRequest(String method, String path, Map<String, String> pathVariables) {
        return MockServerRequest.builder()
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(URI.create(path))
                .pathVariables(pathVariables)
                .build();
    }

    @Test
    void getById_shouldReturnValidationForInvalidId() {
        ServerRequest request = createRequest("GET", "/api/triggers/evaluations/abc", Map.of("evaluationId", "abc"));

        StepVerifier.create(
                        triggerEvaluationHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .expectError(ValidationException.class)
                .verify();
    }
}
