package org.example.financialservice.repository;

import org.example.financialservice.model.Quote;
import org.example.financialservice.model.QuoteStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface QuoteRepository extends ReactiveCrudRepository<Quote,Long>{
    Flux<Quote> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Mono<Quote> findByIdAndCustomerId(Long id, Long customerId);
    Mono<Quote> findByQuoteReference(String quoteReference);
    Mono<Boolean> existsByCustomerIdAndProductIdAndLocationZoneIdAndStatus(
            Long customerId,
            Long productId,
            Long locationZoneId,
            QuoteStatus status
    );
    Mono<Quote> findByPolicyIdAndStatus(
            Long policyId,
            QuoteStatus status
    );
    Flux<Quote> findAllByPolicyIdAndStatus(
            Long policyId,
            QuoteStatus status
    );
    Mono<Long> countByStatus(QuoteStatus status);
}