package org.example.notificationservice.service;

import org.example.notificationservice.dto.AuditEventResponse;
import org.example.notificationservice.dto.InternalAuditEventRequest;
import org.example.notificationservice.exception.ForbiddenException;
import org.example.notificationservice.model.AuditEvent;
import org.example.notificationservice.repository.AuditEventRepository;
import org.example.notificationservice.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

    @Mock
    private AuditEventRepository repository;

    @InjectMocks
    private AuditEventService service;

    private MockedStatic<SecurityUtil> securityUtilMock;

    @BeforeEach
    void setUp() {
        securityUtilMock = mockStatic(SecurityUtil.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    private AuditEvent buildAuditEvent(Long id, String eventType, LocalDateTime createdAt) {
        return AuditEvent.builder()
                .id(id)
                .eventReference("AUD-TEST-" + id)
                .eventType(eventType)
                .actorUserId(1L)
                .actorRole("ROLE_ADMIN")
                .entityType("POLICY")
                .entityId(100L)
                .description("Test event")
                .correlationId("corr-123")
                .createdAt(createdAt)
                .build();
    }

    @Test
    void create_validRequest_savesAndReturnsResponse() {
        InternalAuditEventRequest request = InternalAuditEventRequest.builder()
                .eventType("POLICY_CREATED")
                .actorUserId(1L)
                .actorRole("ROLE_ADMIN")
                .entityType("POLICY")
                .entityId(100L)
                .description("Policy was created")
                .correlationId("corr-123")
                .build();

        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> {
            AuditEvent e = invocation.getArgument(0);
            e.setId(1L);
            return Mono.just(e);
        });

        StepVerifier.create(service.create(request))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getEventReference()).startsWith("AUD-");
                    assertThat(response.getEventType()).isEqualTo("POLICY_CREATED");
                    assertThat(response.getActorUserId()).isEqualTo(1L);
                    assertThat(response.getEntityType()).isEqualTo("POLICY");
                    assertThat(response.getCreatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(repository).save(any(AuditEvent.class));
    }

    @Test
    void create_titleAndMessageTrimmed() {
        InternalAuditEventRequest request = InternalAuditEventRequest.builder()
                .eventType("  POLICY_CREATED  ")
                .entityType("  POLICY  ")
                .description("  Policy was created  ")
                .build();

        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> {
            AuditEvent e = invocation.getArgument(0);
            e.setId(1L);
            return Mono.just(e);
        });

        StepVerifier.create(service.create(request))
                .assertNext(response -> {
                    assertThat(response.getEventType()).isEqualTo("POLICY_CREATED");
                    assertThat(response.getEntityType()).isEqualTo("POLICY");
                    assertThat(response.getDescription()).isEqualTo("Policy was created");
                })
                .verifyComplete();
    }

    @Test
    void all_adminUser_returnsAllEvents() {
        securityUtilMock.when(SecurityUtil::userId).thenReturn(Mono.just(1L));
        securityUtilMock.when(SecurityUtil::customerId).thenReturn(Mono.just(100L));
        securityUtilMock.when(SecurityUtil::role).thenReturn(Mono.just("ROLE_ADMIN"));
        securityUtilMock.when(SecurityUtil::requireAdmin).thenReturn(Mono.empty());

        AuditEvent e1 = buildAuditEvent(1L, "EVENT_A", LocalDateTime.now().minusHours(1));
        AuditEvent e2 = buildAuditEvent(2L, "EVENT_B", LocalDateTime.now());

        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(Flux.just(e2, e1));

        StepVerifier.create(service.all())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void all_nonAdmin_throwsForbiddenException() {
        securityUtilMock.when(SecurityUtil::userId).thenReturn(Mono.just(1L));
        securityUtilMock.when(SecurityUtil::customerId).thenReturn(Mono.just(100L));
        securityUtilMock.when(SecurityUtil::role).thenReturn(Mono.just("ROLE_USER"));
        securityUtilMock.when(SecurityUtil::requireAdmin)
                .thenReturn(Mono.error(new ForbiddenException("Admin access required")));

        StepVerifier.create(service.all())
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void all_noEvents_returnsEmpty() {
        securityUtilMock.when(SecurityUtil::userId).thenReturn(Mono.just(1L));
        securityUtilMock.when(SecurityUtil::customerId).thenReturn(Mono.just(100L));
        securityUtilMock.when(SecurityUtil::role).thenReturn(Mono.just("ROLE_ADMIN"));
        securityUtilMock.when(SecurityUtil::requireAdmin).thenReturn(Mono.empty());

        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(Flux.empty());

        StepVerifier.create(service.all())
                .verifyComplete();
    }
}
