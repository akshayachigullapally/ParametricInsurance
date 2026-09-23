package org.example.triggerservice.handler;

import org.example.triggerservice.dto.TriggerDefinitionResponse;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.example.triggerservice.service.TriggerDefinitionService;
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

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriggerDefinitionHandlerTest {

    @Mock
    private TriggerDefinitionService triggerDefinitionService;

    @Mock
    private RequestValidationUtil validationUtil;

    @InjectMocks
    private TriggerDefinitionHandler triggerDefinitionHandler;

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
    void getAll_shouldReturnDefinitionsForAdmin() {
        TriggerDefinitionResponse response = TriggerDefinitionResponse.builder()
                .id(1L)
                .definitionCode("DEF-001")
                .status(TriggerDefinitionStatus.ACTIVE)
                .build();

        when(triggerDefinitionService.getAll()).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/definitions");

        StepVerifier.create(
                        triggerDefinitionHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getAll_shouldReturnForbiddenForCustomer() {
        ServerRequest request = createRequest("GET", "/api/triggers/definitions");

        StepVerifier.create(
                        triggerDefinitionHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void getActive_shouldReturnActiveDefinitions() {
        TriggerDefinitionResponse response = TriggerDefinitionResponse.builder()
                .id(1L)
                .definitionCode("DEF-001")
                .status(TriggerDefinitionStatus.ACTIVE)
                .build();

        when(triggerDefinitionService.getActive()).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/definitions/active");

        StepVerifier.create(
                        triggerDefinitionHandler.getActive(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getById_shouldReturnDefinition() {
        TriggerDefinitionResponse response = TriggerDefinitionResponse.builder()
                .id(1L)
                .definitionCode("DEF-001")
                .build();

        when(triggerDefinitionService.getById(1L)).thenReturn(Mono.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/definitions/1", Map.of("definitionId", "1"));

        StepVerifier.create(
                        triggerDefinitionHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getById_shouldReturnValidationForInvalidId() {
        ServerRequest request = createRequest("GET", "/api/triggers/definitions/abc", Map.of("definitionId", "abc"));

        StepVerifier.create(
                        triggerDefinitionHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void getByPerilId_shouldReturnDefinitions() {
        TriggerDefinitionResponse response = TriggerDefinitionResponse.builder()
                .id(1L)
                .perilId(1L)
                .build();

        when(triggerDefinitionService.getByPerilId(1L)).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/definitions/peril/1", Map.of("perilId", "1"));

        StepVerifier.create(
                        triggerDefinitionHandler.getByPerilId(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getByZoneId_shouldReturnDefinitions() {
        TriggerDefinitionResponse response = TriggerDefinitionResponse.builder()
                .id(1L)
                .locationZoneId(1L)
                .build();

        when(triggerDefinitionService.getByLocationZoneId(1L)).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/definitions/zone/1", Map.of("zoneId", "1"));

        StepVerifier.create(
                        triggerDefinitionHandler.getByZoneId(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getByProductId_shouldReturnDefinitions() {
        TriggerDefinitionResponse response = TriggerDefinitionResponse.builder()
                .id(1L)
                .productId(1L)
                .build();

        when(triggerDefinitionService.getByProductId(1L)).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/definitions/product/1", Map.of("productId", "1"));

        StepVerifier.create(
                        triggerDefinitionHandler.getByProductId(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }
}
