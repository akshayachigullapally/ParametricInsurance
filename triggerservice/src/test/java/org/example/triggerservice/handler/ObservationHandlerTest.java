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
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

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

    @Test
    void getAll_shouldReturnObservationsForAdmin() {
        ObservationResponse response = ObservationResponse.builder()
                .id(1L)
                .observationReference("OBS-001")
                .measurementType(MeasurementType.RAINFALL)
                .observedValue(new BigDecimal("25.50"))
                .status(ObservationStatus.VALIDATED)
                .build();

        when(observationService.getAll()).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/observations");

        StepVerifier.create(
                        observationHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getAll_shouldReturnForbiddenForCustomer() {
        ServerRequest request = createRequest("GET", "/api/triggers/observations");

        StepVerifier.create(
                        observationHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void getById_shouldReturnObservation() {
        ObservationResponse response = ObservationResponse.builder()
                .id(1L)
                .observationReference("OBS-001")
                .build();

        when(observationService.getById(1L)).thenReturn(Mono.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/observations/1");

        StepVerifier.create(
                        observationHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getById_shouldReturnValidationForInvalidId() {
        ServerRequest request = createRequest("GET", "/api/triggers/observations/abc");

        StepVerifier.create(
                        observationHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void getByZoneId_shouldReturnObservations() {
        ObservationResponse response = ObservationResponse.builder()
                .id(1L)
                .locationZoneId(1L)
                .build();

        when(observationService.getByZoneId(1L)).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/observations/zone/1");

        StepVerifier.create(
                        observationHandler.getByZoneId(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getByMeasurementType_shouldReturnObservations() {
        ObservationResponse response = ObservationResponse.builder()
                .id(1L)
                .measurementType(MeasurementType.TEMPERATURE)
                .build();

        when(observationService.getByMeasurementType("TEMPERATURE")).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/observations/measurement-type/TEMPERATURE");

        StepVerifier.create(
                        observationHandler.getByMeasurementType(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }
}
