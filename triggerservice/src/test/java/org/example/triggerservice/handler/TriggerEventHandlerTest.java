package org.example.triggerservice.handler;

import org.example.triggerservice.dto.TriggerEventResponse;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.TriggerEventStatus;
import org.example.triggerservice.service.TriggerEventService;
import org.example.triggerservice.util.RequestValidationUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriggerEventHandlerTest {

    @Mock
    private TriggerEventService triggerEventService;

    @Mock
    private RequestValidationUtil validationUtil;

    @InjectMocks
    private TriggerEventHandler triggerEventHandler;

    private ServerRequest createRequest(String method, String path) {
        MockServerHttpRequest httpRequest = MockServerHttpRequest.method(
                org.springframework.http.HttpMethod.valueOf(method), path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
        return ServerRequest.create(exchange, List.of());
    }

    @Test
    void getAll_shouldReturnEventsForAuthorizedRole() {
        TriggerEventResponse response = TriggerEventResponse.builder()
                .id(1L)
                .eventReference("EVT-001")
                .status(TriggerEventStatus.DETECTED)
                .build();

        when(triggerEventService.getAll()).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/events");

        StepVerifier.create(
                        triggerEventHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getAll_shouldReturnForbiddenForUnauthorizedRole() {
        ServerRequest request = createRequest("GET", "/api/triggers/events");

        StepVerifier.create(
                        triggerEventHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void getById_shouldReturnEventForAuthorizedRole() {
        TriggerEventResponse response = TriggerEventResponse.builder()
                .id(1L)
                .eventReference("EVT-001")
                .status(TriggerEventStatus.DETECTED)
                .build();

        when(triggerEventService.getById(1L)).thenReturn(Mono.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/events/1");

        StepVerifier.create(
                        triggerEventHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getById_shouldReturnValidationForInvalidId() {
        ServerRequest request = createRequest("GET", "/api/triggers/events/abc");

        StepVerifier.create(
                        triggerEventHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void getByStatus_shouldReturnEvents() {
        TriggerEventResponse response = TriggerEventResponse.builder()
                .id(1L)
                .status(TriggerEventStatus.DETECTED)
                .build();

        when(triggerEventService.getByStatus("DETECTED")).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/events/status/DETECTED");

        StepVerifier.create(
                        triggerEventHandler.getByStatus(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getByDefinitionId_shouldReturnEvents() {
        TriggerEventResponse response = TriggerEventResponse.builder()
                .id(1L)
                .triggerDefinitionId(1L)
                .build();

        when(triggerEventService.getByDefinitionId(1L)).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/events/definition/1");

        StepVerifier.create(
                        triggerEventHandler.getByDefinitionId(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getByProductId_shouldReturnEvents() {
        TriggerEventResponse response = TriggerEventResponse.builder()
                .id(1L)
                .productId(1L)
                .build();

        when(triggerEventService.getByProductId(1L)).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/events/product/1");

        StepVerifier.create(
                        triggerEventHandler.getByProductId(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getByZoneId_shouldReturnEvents() {
        TriggerEventResponse response = TriggerEventResponse.builder()
                .id(1L)
                .locationZoneId(1L)
                .build();

        when(triggerEventService.getByZoneId(1L)).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/events/zone/1");

        StepVerifier.create(
                        triggerEventHandler.getByZoneId(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }
}
