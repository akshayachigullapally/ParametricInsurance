package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.PerilRequest;
import org.example.triggerservice.dto.PerilResponse;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.Peril;
import org.example.triggerservice.model.PerilStatus;
import org.example.triggerservice.repository.PerilRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerilService{
    private final PerilRepository perilRepository;
    private final NotificationService notificationService;

    public Flux<PerilResponse> getAll(){
        log.debug("Get all [peril]");
        return perilRepository.findAllByStatusNotOrderByPerilNameAsc(PerilStatus.ARCHIVED)
                .map(this::toResponse);
    }

    public Flux<PerilResponse> getActive(){
        log.debug("Get active [peril]");
        return perilRepository.findAllByStatusOrderByPerilNameAsc(PerilStatus.ACTIVE)
                .map(this::toResponse);
    }

    public Mono<PerilResponse> getById(Long perilId){
        log.debug("Get by id [peril]: perilId={}",perilId);
        return findActiveRecord(perilId).map(this::toResponse);
    }

    public Mono<PerilResponse> create(PerilRequest request,Long userId){
        log.info("Create [peril]: userId={}",userId);
        normalize(request);
        return perilRepository.existsByPerilCodeIgnoreCase(request.getPerilCode())
                .flatMap(exists->{
                    if(exists) return Mono.error(new ConflictException("Peril code already exists"));

                    LocalDateTime now=LocalDateTime.now();
                    Peril peril=Peril.builder()
                            .perilCode(request.getPerilCode())
                            .perilName(request.getPerilName())
                            .description(request.getDescription())
                            .status(PerilStatus.ACTIVE)
                            .createdBy(userId)
                            .createdAt(now)
                            .updatedBy(userId)
                            .updatedAt(now)
                            .build();

                    return perilRepository.save(peril);
                })
                .flatMap(saved->notificationService.audit(
                        "PERIL_CREATED","PERIL",saved.getId(),userId,
                        "Created peril "+saved.getPerilName()+" ("+saved.getPerilCode()+")"
                ).thenReturn(saved))
                .map(this::toResponse);
    }

    public Mono<PerilResponse> update(Long perilId,PerilRequest request,Long userId){
        log.info("Update [peril]: perilId={}, userId={}",perilId,userId);
        normalize(request);
        return findActiveRecord(perilId)
                .flatMap(peril->perilRepository.existsByPerilCodeAndIdNot(request.getPerilCode(),perilId)
                        .flatMap(exists->{
                            if(exists) return Mono.error(new ConflictException("Peril code already exists"));

                            peril.setPerilCode(request.getPerilCode());
                            peril.setPerilName(request.getPerilName());
                            peril.setDescription(request.getDescription());
                            peril.setUpdatedBy(userId);
                            peril.setUpdatedAt(LocalDateTime.now());
                            return perilRepository.save(peril);
                        }))
                .flatMap(saved->notificationService.audit(
                        "PERIL_UPDATED","PERIL",saved.getId(),userId,
                        "Updated peril "+saved.getPerilName()+" ("+saved.getPerilCode()+")"
                ).thenReturn(saved))
                .map(this::toResponse);
    }

    public Mono<PerilResponse> updateStatus(Long perilId,String statusValue,Long userId){
        log.info("Update status [peril]: perilId={}, statusValue={}, userId={}",perilId,statusValue,userId);
        PerilStatus status=parseStatus(statusValue);
        if(status==PerilStatus.ARCHIVED)
            return Mono.error(new ValidationException("Use the delete endpoint to archive a peril"));

        return findActiveRecord(perilId)
                .flatMap(peril->{
                    peril.setStatus(status);
                    peril.setUpdatedBy(userId);
                    peril.setUpdatedAt(LocalDateTime.now());
                    return perilRepository.save(peril);
                })
                .flatMap(saved->notificationService.audit(
                        "PERIL_STATUS_CHANGED","PERIL",saved.getId(),userId,
                        "Changed status of peril "+saved.getPerilCode()+" to "+saved.getStatus()
                ).thenReturn(saved))
                .map(this::toResponse);
    }

    public Mono<Void> archive(Long perilId,Long userId){
        log.info("Archive [peril]: perilId={}, userId={}",perilId,userId);
        return findActiveRecord(perilId)
                .flatMap(peril->{
                    peril.setStatus(PerilStatus.ARCHIVED);
                    peril.setUpdatedBy(userId);
                    peril.setUpdatedAt(LocalDateTime.now());
                    return perilRepository.save(peril);
                })
                .flatMap(saved->notificationService.audit(
                        "PERIL_ARCHIVED","PERIL",saved.getId(),userId,
                        "Archived peril "+saved.getPerilCode()
                ))
                .then();
    }

    private Mono<Peril> findActiveRecord(Long perilId){
        return perilRepository.findById(perilId)
                .filter(peril->peril.getStatus()!=PerilStatus.ARCHIVED)
                .switchIfEmpty(Mono.error(new NotFoundException("Peril not found")));
    }

    private PerilStatus parseStatus(String value){
        try{
            return PerilStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }catch(Exception exception){
            throw new ValidationException("Invalid peril status. Allowed values are ACTIVE and INACTIVE");
        }
    }

    private void normalize(PerilRequest request){
        request.setPerilCode(normalizeCode(request.getPerilCode()));
        request.setPerilName(normalizeText(request.getPerilName()));
        request.setDescription(normalizeText(request.getDescription()));
    }

    private String normalizeCode(String value){
        if(!StringUtils.hasText(value)) return value;
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_");
    }

    private String normalizeText(String value){
        return StringUtils.hasText(value)?value.trim():value;
    }

    private PerilResponse toResponse(Peril peril){
        return PerilResponse.builder()
                .id(peril.getId())
                .perilCode(peril.getPerilCode())
                .perilName(peril.getPerilName())
                .description(peril.getDescription())
                .status(peril.getStatus())
                .createdBy(peril.getCreatedBy())
                .createdAt(peril.getCreatedAt())
                .updatedBy(peril.getUpdatedBy())
                .updatedAt(peril.getUpdatedAt())
                .build();
    }
}