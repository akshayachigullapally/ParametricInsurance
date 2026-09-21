package org.example.productservice.repository;

import org.example.productservice.model.Coverage;
import org.example.productservice.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageRepositoryTest extends BaseRepositoryTest {
    @Autowired private CoverageRepository coverageRepository;

    private Product savedProduct;
    private Product otherProduct;

    @BeforeEach
    void prepareProducts() {
        Mono<Void> setup=productRepository.save(
                        activeProduct("WEATHER-01","Weather Shield","WEATHER"))
                .flatMap(first->productRepository.save(
                                activeProduct("FLOOD-01","Flood Shield","FLOOD"))
                        .map(second->List.of(first,second)))
                .doOnNext(products->{
                    savedProduct=products.get(0);
                    otherProduct=products.get(1);
                })
                .then();

        StepVerifier.create(setup).verifyComplete();

        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.getId()).isNotNull();
        assertThat(otherProduct).isNotNull();
        assertThat(otherProduct.getId()).isNotNull();
    }

    @Test
    void findAllByProductId_shouldReturnOnlyMatchingProductCoverages() {
        Flux<Coverage> operation=coverageRepository.saveAll(Flux.just(
                        coverage(savedProduct.getId(),"WEATHER-BASIC","Basic",true,true),
                        coverage(savedProduct.getId(),"WEATHER-EXTRA","Extra",false,true),
                        coverage(otherProduct.getId(),"FLOOD-BASIC","Flood Basic",true,true)
                ))
                .thenMany(coverageRepository.findAllByProductId(savedProduct.getId()));

        StepVerifier.create(operation.collectList())
                .assertNext(result->{
                    assertThat(result).hasSize(2);
                    assertThat(result)
                            .extracting(Coverage::getProductId)
                            .containsOnly(savedProduct.getId());
                    assertThat(result)
                            .extracting(Coverage::getCoverageCode)
                            .containsExactlyInAnyOrder("WEATHER-BASIC","WEATHER-EXTRA");
                })
                .verifyComplete();
    }

    @Test
    void findAllByProductIdAndActiveTrue_shouldReturnOnlyActiveProductCoverages() {
        Flux<Coverage> operation=coverageRepository.saveAll(Flux.just(
                        coverage(savedProduct.getId(),"WEATHER-BASIC","Basic",true,true),
                        coverage(savedProduct.getId(),"WEATHER-EXTRA","Extra",false,true),
                        coverage(savedProduct.getId(),"WEATHER-OLD","Old",false,false),
                        coverage(otherProduct.getId(),"FLOOD-BASIC","Flood Basic",true,true)
                ))
                .thenMany(
                        coverageRepository.findAllByProductIdAndActiveTrue(savedProduct.getId())
                );

        StepVerifier.create(operation.collectList())
                .assertNext(result->{
                    assertThat(result).hasSize(2);
                    assertThat(result)
                            .extracting(Coverage::getProductId)
                            .containsOnly(savedProduct.getId());
                    assertThat(result)
                            .extracting(Coverage::getActive)
                            .containsOnly(true);
                    assertThat(result)
                            .extracting(Coverage::getCoverageCode)
                            .containsExactlyInAnyOrder("WEATHER-BASIC","WEATHER-EXTRA");
                })
                .verifyComplete();
    }

    @Test
    void findByIdAndProductId_whenProductMatches_shouldReturnCoverage() {
        Mono<Coverage> operation=coverageRepository.save(
                        coverage(savedProduct.getId(),"WEATHER-BASIC","Basic",true,true))
                .flatMap(saved->coverageRepository.findByIdAndProductId(
                        saved.getId(),savedProduct.getId()
                ));

        StepVerifier.create(operation)
                .assertNext(found->{
                    assertThat(found.getId()).isNotNull();
                    assertThat(found.getProductId()).isEqualTo(savedProduct.getId());
                    assertThat(found.getCoverageCode()).isEqualTo("WEATHER-BASIC");
                })
                .verifyComplete();
    }

    @Test
    void findByIdAndProductId_whenProductDoesNotMatch_shouldReturnEmpty() {
        Mono<Coverage> operation=coverageRepository.save(
                        coverage(savedProduct.getId(),"WEATHER-BASIC","Basic",true,true))
                .flatMap(saved->coverageRepository.findByIdAndProductId(
                        saved.getId(),otherProduct.getId()
                ));

        StepVerifier.create(operation).verifyComplete();
    }

    @Test
    void existsByProductIdAndCoverageCodeIgnoreCase_whenMatchingCoverageExists_shouldReturnTrue() {
        Mono<Boolean> operation=coverageRepository.save(
                        coverage(savedProduct.getId(),"WEATHER-BASIC","Basic",true,true))
                .then(coverageRepository.existsByProductIdAndCoverageCodeIgnoreCase(
                        savedProduct.getId(),"weather-basic"
                ));

        StepVerifier.create(operation)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void existsByProductIdAndCoverageCodeIgnoreCase_whenCodeDoesNotExist_shouldReturnFalse() {
        Mono<Boolean> operation=
                coverageRepository.existsByProductIdAndCoverageCodeIgnoreCase(
                        savedProduct.getId(),"UNKNOWN"
                );

        StepVerifier.create(operation)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void existsByProductIdAndCoverageCodeIgnoreCase_whenProductDoesNotMatch_shouldReturnFalse() {
        Mono<Boolean> operation=coverageRepository.save(
                        coverage(savedProduct.getId(),"WEATHER-BASIC","Basic",true,true))
                .then(coverageRepository.existsByProductIdAndCoverageCodeIgnoreCase(
                        otherProduct.getId(),"WEATHER-BASIC"
                ));

        StepVerifier.create(operation)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void deleteAllByProductId_shouldDeleteOnlyMatchingProductCoverages() {
        Mono<List<Coverage>> operation=coverageRepository.saveAll(Flux.just(
                        coverage(savedProduct.getId(),"WEATHER-BASIC","Basic",true,true),
                        coverage(savedProduct.getId(),"WEATHER-EXTRA","Extra",false,true),
                        coverage(otherProduct.getId(),"FLOOD-BASIC","Flood Basic",true,true)
                ))
                .then(coverageRepository.deleteAllByProductId(savedProduct.getId()))
                .thenMany(coverageRepository.findAll())
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->{
                    assertThat(result).hasSize(1);
                    assertThat(result.get(0).getProductId()).isEqualTo(otherProduct.getId());
                    assertThat(result.get(0).getCoverageCode()).isEqualTo("FLOOD-BASIC");
                })
                .verifyComplete();
    }

    private Coverage coverage(Long productId,String code,String name,boolean mandatory,boolean active) {
        LocalDateTime now=LocalDateTime.now();

        return Coverage.builder()
                .productId(productId)
                .coverageCode(code)
                .name(name)
                .description(name+" description")
                .coverageLimit(new BigDecimal("100000.00"))
                .mandatory(mandatory)
                .active(active)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}