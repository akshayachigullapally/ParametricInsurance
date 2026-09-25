package org.example.triggerservice.service;

import org.example.triggerservice.dto.PerilRequest;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.Peril;
import org.example.triggerservice.model.PerilStatus;
import org.example.triggerservice.repository.PerilRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerilServiceTest {
    @Mock private PerilRepository perilRepository;
    @Mock private NotificationService notificationService;
    @InjectMocks private PerilService perilService;

    @BeforeEach
    void stubAudit(){
        lenient().when(notificationService.audit(any(),any(),any(),any(),any()))
                .thenReturn(Mono.empty());
    }

    @Test
    void getAll_shouldReturnNonArchivedPerils() {
        when(perilRepository.findAllByStatusNotOrderByPerilNameAsc(PerilStatus.ARCHIVED))
                .thenReturn(Flux.just(
                        peril(1L,"FLOOD","Flood",PerilStatus.ACTIVE),
                        peril(2L,"WIND","Wind",PerilStatus.INACTIVE)
                ));

        StepVerifier.create(perilService.getAll())
                .assertNext(result->assertThat(result.getPerilCode()).isEqualTo("FLOOD"))
                .assertNext(result->assertThat(result.getPerilCode()).isEqualTo("WIND"))
                .verifyComplete();
    }

    @Test
    void getActive_shouldReturnActivePerils() {
        when(perilRepository.findAllByStatusOrderByPerilNameAsc(PerilStatus.ACTIVE))
                .thenReturn(Flux.just(peril(1L,"FLOOD","Flood",PerilStatus.ACTIVE)));

        StepVerifier.create(perilService.getActive())
                .assertNext(result->assertThat(result.getStatus()).isEqualTo(PerilStatus.ACTIVE))
                .verifyComplete();
    }

    @Test
    void getById_whenPerilExists_shouldReturnPeril() {
        when(perilRepository.findById(1L))
                .thenReturn(Mono.just(peril(1L,"FLOOD","Flood",PerilStatus.ACTIVE)));

        StepVerifier.create(perilService.getById(1L))
                .assertNext(result->assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void getById_whenPerilDoesNotExist_shouldReturnNotFound() {
        when(perilRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(perilService.getById(99L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void create_shouldNormalizeAndSavePeril() {
        PerilRequest request=request(" flood damage "," Flood Damage ");

        when(perilRepository.existsByPerilCodeIgnoreCase("FLOOD_DAMAGE"))
                .thenReturn(Mono.just(false));
        when(perilRepository.save(any(Peril.class))).thenAnswer(invocation->{
            Peril saved=invocation.getArgument(0);
            saved.setId(1L);
            return Mono.just(saved);
        });

        StepVerifier.create(perilService.create(request,10L))
                .assertNext(result->{
                    assertThat(result.getId()).isEqualTo(1L);
                    assertThat(result.getPerilCode()).isEqualTo("FLOOD_DAMAGE");
                    assertThat(result.getPerilName()).isEqualTo("Flood Damage");
                    assertThat(result.getStatus()).isEqualTo(PerilStatus.ACTIVE);
                })
                .verifyComplete();
    }

    @Test
    void create_whenCodeExists_shouldReturnConflict() {
        when(perilRepository.existsByPerilCodeIgnoreCase("FLOOD"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(perilService.create(request("flood","Flood"),10L))
                .expectError(ConflictException.class)
                .verify();

        verify(perilRepository,never()).save(any());
    }

    @Test
    void update_shouldUpdatePeril() {
        when(perilRepository.findById(1L))
                .thenReturn(Mono.just(peril(1L,"OLD","Old",PerilStatus.ACTIVE)));
        when(perilRepository.existsByPerilCodeAndIdNot("FLOOD",1L))
                .thenReturn(Mono.just(false));
        when(perilRepository.save(any(Peril.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(perilService.update(1L,request("flood","Flood"),20L))
                .assertNext(result->{
                    assertThat(result.getPerilCode()).isEqualTo("FLOOD");
                    assertThat(result.getPerilName()).isEqualTo("Flood");
                    assertThat(result.getUpdatedBy()).isEqualTo(20L);
                })
                .verifyComplete();
    }

    @Test
    void update_whenCodeExists_shouldReturnConflict() {
        when(perilRepository.findById(1L))
                .thenReturn(Mono.just(peril(1L,"OLD","Old",PerilStatus.ACTIVE)));
        when(perilRepository.existsByPerilCodeAndIdNot("FLOOD",1L))
                .thenReturn(Mono.just(true));

        StepVerifier.create(perilService.update(1L,request("flood","Flood"),20L))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void updateStatus_shouldUpdatePerilStatus() {
        when(perilRepository.findById(1L))
                .thenReturn(Mono.just(peril(1L,"FLOOD","Flood",PerilStatus.ACTIVE)));
        when(perilRepository.save(any(Peril.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(perilService.updateStatus(1L,"inactive",20L))
                .assertNext(result->assertThat(result.getStatus())
                        .isEqualTo(PerilStatus.INACTIVE))
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

    @Test
    void archive_shouldSaveArchivedPeril() {
        when(perilRepository.findById(1L))
                .thenReturn(Mono.just(peril(1L,"FLOOD","Flood",PerilStatus.ACTIVE)));
        when(perilRepository.save(any(Peril.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(perilService.archive(1L,20L)).verifyComplete();

        ArgumentCaptor<Peril> captor=ArgumentCaptor.forClass(Peril.class);
        verify(perilRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PerilStatus.ARCHIVED);
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(20L);
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