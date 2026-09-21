package org.example.triggerservice.handler;

import org.example.triggerservice.dto.PerilResponse;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.PerilStatus;
import org.example.triggerservice.service.PerilService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerilHandlerTest {

    @Mock
    private PerilService perilService;

    @Mock
    private RequestValidationUtil validationUtil;

    @InjectMocks
    private PerilHandler perilHandler;

    private ServerRequest createRequest(String method, String path) {
        MockServerHttpRequest httpRequest = MockServerHttpRequest.method(
                org.springframework.http.HttpMethod.valueOf(method), path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
        return ServerRequest.create(exchange, List.of());
    }

    @Test
    void getAll_shouldReturnPerilsForAdmin() {
        PerilResponse response = PerilResponse.builder()
                .id(1L)
                .perilCode("P1")
                .perilName("Peril 1")
                .status(PerilStatus.ACTIVE)
                .build();

        when(perilService.getAll()).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/perils");

        StepVerifier.create(
                        perilHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getAll_shouldReturnPerilsForUnderwriter() {
        PerilResponse response = PerilResponse.builder()
                .id(1L)
                .perilCode("P1")
                .build();

        when(perilService.getAll()).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/perils");

        StepVerifier.create(
                        perilHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_UNDERWRITER"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getAll_shouldReturnForbiddenForCustomer() {
        ServerRequest request = createRequest("GET", "/api/triggers/perils");

        StepVerifier.create(
                        perilHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void getById_shouldReturnPeril() {
        PerilResponse response = PerilResponse.builder()
                .id(1L)
                .perilCode("P1")
                .build();

        when(perilService.getById(1L)).thenReturn(Mono.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/perils/1");

        StepVerifier.create(
                        perilHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getById_shouldReturnValidationForInvalidId() {
        ServerRequest request = createRequest("GET", "/api/triggers/perils/abc");

        StepVerifier.create(
                        perilHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .expectError(ValidationException.class)
                .verify();
    }
}
