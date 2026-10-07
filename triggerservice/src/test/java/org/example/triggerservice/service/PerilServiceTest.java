package org.example.triggerservice.service;

import org.example.triggerservice.dto.PerilRequest;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.Peril;
import org.example.triggerservice.model.PerilStatus;
import org.example.triggerservice.repository.PerilRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerilServiceTest {
    @Mock private PerilRepository perilRepository;
    @Mock private NotificationService notificationService;
    @InjectMocks private PerilService perilService;

    @Test
    void getActive_shouldReturnActivePerils() {
        when(perilRepository.findAllByStatusOrderByPerilNameAsc(PerilStatus.ACTIVE))
                .thenReturn(Flux.just(peril(1L,"FLOOD","Flood",PerilStatus.ACTIVE)));

        StepVerifier.create(perilService.getActive())
                .assertNext(result->assertThat(result.getStatus()).isEqualTo(PerilStatus.ACTIVE))
                .verifyComplete();
    }

    @Test
    void updateStatus_whenArchivedRequested_shouldReturnValidationError() {
        StepVerifier.create(perilService.updateStatus(1L,"ARCHIVED",20L))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(perilRepository);
    }

    @Test
    void updateStatus_whenValueIsInvalid_shouldReturnValidationError() {
        StepVerifier.create(Mono.defer(
                        ()->perilService.updateStatus(1L,"UNKNOWN",20L)))
                .expectError(ValidationException.class)
                .verify();
    }

    private Peril peril(Long id,String code,String name,PerilStatus status) {
        LocalDateTime now=LocalDateTime.now();
        return Peril.builder().id(id).perilCode(code).perilName(name)
                .description(name+" description").status(status).createdBy(1L)
                .createdAt(now).updatedBy(1L).updatedAt(now).build();
    }

    private PerilRequest request(String code,String name) {
        return PerilRequest.builder().perilCode(code).perilName(name)
                .description(" Description ").build();
    }
}