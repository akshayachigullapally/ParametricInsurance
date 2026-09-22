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
        return idempotencyRecordRepository.existsByIdempotencyKey(idempotencyKey);
    }

        public Mono<Long> resourceId(String idempotencyKey){
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

    public static String buildTriggerEventKey(
            Long definitionId,
            java.time.LocalDateTime windowStart,
            java.time.LocalDateTime windowEnd
    ){
        return "TRIGGER_EVENT:"+definitionId+":"+windowStart+":"+windowEnd;
    }

    public static String buildPolicyEvaluationKey(
            Long policyId,
            Long triggerEventId
    ){
        return "POLICY_EVALUATION:"+policyId+":"+triggerEventId;
    }
}
