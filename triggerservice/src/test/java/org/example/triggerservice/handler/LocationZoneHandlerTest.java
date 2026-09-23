package org.example.triggerservice.handler;

import org.example.triggerservice.dto.LocationZoneResponse;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.service.LocationZoneService;
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
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationZoneHandlerTest {

    @Mock
    private LocationZoneService locationZoneService;

    @Mock
    private RequestValidationUtil validationUtil;

    @InjectMocks
    private LocationZoneHandler locationZoneHandler;

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
    void getAll_shouldReturnZonesForAdmin() {
        LocationZoneResponse response = LocationZoneResponse.builder()
                .id(1L)
                .zoneCode("Z1")
                .zoneName("Zone 1")
                .status(LocationZoneStatus.ACTIVE)
                .build();

        when(locationZoneService.getAll(null)).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/zones");

        StepVerifier.create(
                        locationZoneHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getAll_shouldReturnForbiddenForCustomer() {
        ServerRequest request = createRequest("GET", "/api/triggers/zones");

        StepVerifier.create(
                        locationZoneHandler.getAll(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void getActive_shouldReturnZonesForCustomer() {
        LocationZoneResponse response = LocationZoneResponse.builder()
                .id(1L)
                .zoneCode("Z1")
                .status(LocationZoneStatus.ACTIVE)
                .build();

        when(locationZoneService.getActive()).thenReturn(Flux.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/zones/active");

        StepVerifier.create(
                        locationZoneHandler.getActive(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getById_shouldReturnZone() {
        LocationZoneResponse response = LocationZoneResponse.builder()
                .id(1L)
                .zoneCode("Z1")
                .build();

        when(locationZoneService.getById(1L)).thenReturn(reactor.core.publisher.Mono.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/zones/1", Map.of("zoneId", "1"));

        StepVerifier.create(
                        locationZoneHandler.getById(request)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        new UsernamePasswordAuthenticationToken("1", null,
                                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }
}
