package org.example.notificationservice.handler;

import org.example.notificationservice.dto.AuditEventResponse;
import org.example.notificationservice.dto.InternalAuditEventRequest;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.service.AuditEventService;
import org.example.notificationservice.util.RequestValidationUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventHandlerTest {

    @Mock
    private AuditEventService service;

    @Mock
    private RequestValidationUtil validation;

    @InjectMocks
    private AuditEventHandler handler;

    private AuditEventResponse buildResponse(Long id) {
        return AuditEventResponse.builder()
                .id(id)
                .eventReference("AUD-TEST-" + id)
                .eventType("POLICY_CREATED")
                .actorUserId(1L)
                .actorRole("ROLE_ADMIN")
                .entityType("POLICY")
                .entityId(100L)
                .description("Test event")
                .correlationId("corr-123")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void all_returnsAllAuditEvents() {
        ServerRequest request = mock(ServerRequest.class);
        when(service.all(null, null)).thenReturn(Flux.just(buildResponse(1L), buildResponse(2L)));

        StepVerifier.create(handler.all(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void all_withFilters_passesThemToService() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.queryParam("actorUserId")).thenReturn(java.util.Optional.of("7"));
        when(request.queryParam("actorRole")).thenReturn(java.util.Optional.of("CUSTOMER"));
        when(service.all(7L, "CUSTOMER")).thenReturn(Flux.just(buildResponse(1L)));

        StepVerifier.create(handler.all(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void mine_returnsOk() {
        ServerRequest request = mock(ServerRequest.class);
        when(service.mine()).thenReturn(Flux.just(buildResponse(1L)));

        StepVerifier.create(handler.mine(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void all_noEvents_returnsEmpty() {
        ServerRequest request = mock(ServerRequest.class);
        when(service.all(null, null)).thenReturn(Flux.empty());

        StepVerifier.create(handler.all(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void create_invalidBody_throwsValidationException() {
        ServerRequest request = mock(ServerRequest.class);
        InternalAuditEventRequest body = InternalAuditEventRequest.builder()
                .eventType("")
                .entityType("POLICY")
                .description("Test")
                .build();

        when(request.bodyToMono(InternalAuditEventRequest.class)).thenReturn(Mono.just(body));
        when(validation.validate(any(InternalAuditEventRequest.class)))
                .thenThrow(new ValidationException("eventType must not be blank"));

        assertThatThrownBy(() -> handler.create(request).block())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("eventType must not be blank");
    }
}
