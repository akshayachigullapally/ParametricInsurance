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
    void findByIdAndProductId_whenProductDoesNotMatch_shouldReturnEmpty() {
        Mono<Coverage> operation=coverageRepository.save(
                        coverage(savedProduct.getId(),"WEATHER-BASIC","Basic",true,true))
                .flatMap(saved->coverageRepository.findByIdAndProductId(
                        saved.getId(),otherProduct.getId()
                ));

        StepVerifier.create(operation).verifyComplete();
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