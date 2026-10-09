package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.model.IdempotencyRecord;
import org.example.triggerservice.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService{
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public Mono<Boolean> isDuplicate(String idempotencyKey){
        log.debug("Is duplicate [idempotency]");
        return idempotencyRecordRepository.existsByIdempotencyKey(idempotencyKey);
    }

        public Mono<Long> resourceId(String idempotencyKey){
            log.debug("Resource id [idempotency]");
                return idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey).map(IdempotencyRecord::getResourceId);
        }

    public Mono<IdempotencyRecord> record(
            String idempotencyKey,
            String operationType,
            Long resourceId
    ){
        return idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                .flatMap(existing->Mono.just(existing))
                .switchIfEmpty(Mono.defer(()->{
                    IdempotencyRecord record=IdempotencyRecord.builder()
                            .idempotencyKey(idempotencyKey)
                            .operationType(operationType)
                            .resourceId(resourceId)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return idempotencyRecordRepository.save(record)
                            .doOnNext(r->log.debug(
                                    "Recorded idempotency key: {} for operation: {}",
                                    idempotencyKey, operationType));
                }));
    }
}
