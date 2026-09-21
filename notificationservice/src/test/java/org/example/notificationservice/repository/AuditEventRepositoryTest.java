package org.example.notificationservice.repository;

import org.example.notificationservice.model.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@ActiveProfiles("test")
@Import(R2dbcTestConfig.class)
class AuditEventRepositoryTest {

    @Autowired
    private AuditEventRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll().block();
    }

    private AuditEvent buildAuditEvent(String eventType, Long actorUserId, LocalDateTime createdAt) {
        return AuditEvent.builder()
                .eventReference("AUD-" + System.nanoTime())
                .eventType(eventType)
                .actorUserId(actorUserId)
                .actorRole("ROLE_ADMIN")
                .entityType("POLICY")
                .entityId(1L)
                .description("Test event description")
                .correlationId("corr-123")
                .createdAt(createdAt)
                .build();
    }

    @Test
    void shouldSaveAndFindById() {
        AuditEvent event = buildAuditEvent("POLICY_CREATED", 1L, LocalDateTime.now());

        AuditEvent saved = repository.save(event).block();

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventReference()).startsWith("AUD-");

        StepVerifier.create(repository.findById(saved.getId()))
                .assertNext(e -> {
                    assertThat(e.getEventType()).isEqualTo("POLICY_CREATED");
                    assertThat(e.getActorUserId()).isEqualTo(1L);
                    assertThat(e.getDescription()).isEqualTo("Test event description");
                })
                .verifyComplete();
    }

    @Test
    void findAllByOrderByCreatedAtDesc() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(buildAuditEvent("EVENT_A", 1L, now.minusHours(2))).block();
        repository.save(buildAuditEvent("EVENT_B", 2L, now.minusHours(1))).block();
        repository.save(buildAuditEvent("EVENT_C", 3L, now)).block();

        StepVerifier.create(repository.findAllByOrderByCreatedAtDesc())
                .assertNext(e -> assertThat(e.getEventType()).isEqualTo("EVENT_C"))
                .assertNext(e -> assertThat(e.getEventType()).isEqualTo("EVENT_B"))
                .assertNext(e -> assertThat(e.getEventType()).isEqualTo("EVENT_A"))
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenNoEvents() {
        StepVerifier.create(repository.findAllByOrderByCreatedAtDesc())
                .verifyComplete();
    }

    @Test
    void shouldDeleteById() {
        AuditEvent saved = repository.save(
                buildAuditEvent("DELETE_TEST", 1L, LocalDateTime.now())
        ).block();

        repository.deleteById(saved.getId()).block();

        StepVerifier.create(repository.findById(saved.getId()))
                .verifyComplete();
    }

    @Test
    void shouldCountAll() {
        repository.save(buildAuditEvent("EVENT_1", 1L, LocalDateTime.now())).block();
        repository.save(buildAuditEvent("EVENT_2", 2L, LocalDateTime.now())).block();

        StepVerifier.create(repository.count())
                .assertNext(count -> assertThat(count).isEqualTo(2L))
                .verifyComplete();
    }
}
