package org.example.productservice.repository;

import org.example.productservice.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRepositoryTest extends BaseRepositoryTest {
    @Autowired private ProductRepository repository;

    @Test
    void findByProductCodeIgnoreCase_whenProductExists_shouldReturnProduct() {
        Mono<Product> operation=repository
                .save(activeProduct("WEATHER-01","Weather Shield","WEATHER"))
                .then(repository.findByProductCodeIgnoreCase("weather-01"));

        StepVerifier.create(operation)
                .assertNext(found->{
                    assertThat(found.getId()).isNotNull();
                    assertThat(found.getProductCode()).isEqualTo("WEATHER-01");
                    assertThat(found.getName()).isEqualTo("Weather Shield");
                    assertThat(found.getCategory()).isEqualTo("WEATHER");
                    assertThat(found.getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
    }

    @Test
    void findByProductCodeIgnoreCase_whenProductDoesNotExist_shouldReturnEmpty() {
        StepVerifier.create(repository.findByProductCodeIgnoreCase("UNKNOWN"))
                .verifyComplete();
    }

    @Test
    void existsByProductCodeIgnoreCase_whenProductExists_shouldReturnTrue() {
        Mono<Boolean> operation=repository
                .save(activeProduct("WEATHER-01","Weather Shield","WEATHER"))
                .then(repository.existsByProductCodeIgnoreCase("WeAtHeR-01"));

        StepVerifier.create(operation)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void existsByProductCodeIgnoreCase_whenProductDoesNotExist_shouldReturnFalse() {
        StepVerifier.create(repository.existsByProductCodeIgnoreCase("UNKNOWN"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void findAllByStatus_shouldReturnOnlyMatchingProducts() {
        Mono<List<Product>> operation=repository.saveAll(Flux.just(
                        product("WEATHER-01","Weather Shield","WEATHER","ACTIVE"),
                        product("FLOOD-01","Flood Shield","FLOOD","ACTIVE"),
                        product("WIND-01","Wind Shield","WIND","DRAFT")
                ))
                .thenMany(repository.findAllByStatus("ACTIVE"))
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->{
                    assertThat(result).hasSize(2);
                    assertThat(result)
                            .extracting(Product::getStatus)
                            .containsOnly("ACTIVE");
                    assertThat(result)
                            .extracting(Product::getProductCode)
                            .containsExactlyInAnyOrder("WEATHER-01","FLOOD-01");
                })
                .verifyComplete();
    }

    @Test
    void findAllByStatus_whenNoProductMatches_shouldReturnEmpty() {
        Mono<List<Product>> operation=repository
                .save(product("WEATHER-01","Weather Shield","WEATHER","DRAFT"))
                .thenMany(repository.findAllByStatus("ACTIVE"))
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void findAllByCategoryAndStatus_shouldApplyBothFilters() {
        Mono<List<Product>> operation=repository.saveAll(Flux.just(
                        product("WEATHER-01","Weather Shield","WEATHER","ACTIVE"),
                        product("WEATHER-02","Storm Shield","WEATHER","DRAFT"),
                        product("FLOOD-01","Flood Shield","FLOOD","ACTIVE")
                ))
                .thenMany(repository.findAllByCategoryAndStatus("WEATHER","ACTIVE"))
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->{
                    assertThat(result).hasSize(1);
                    assertThat(result.get(0).getProductCode()).isEqualTo("WEATHER-01");
                    assertThat(result.get(0).getCategory()).isEqualTo("WEATHER");
                    assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
    }

    @Test
    void findAllByCategoryAndStatus_whenNoProductMatches_shouldReturnEmpty() {
        Mono<List<Product>> operation=repository
                .save(product("WEATHER-01","Weather Shield","WEATHER","ACTIVE"))
                .thenMany(repository.findAllByCategoryAndStatus("FLOOD","ACTIVE"))
                .collectList();

        StepVerifier.create(operation)
                .assertNext(result->assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void findByIdAndStatus_whenIdAndStatusMatch_shouldReturnProduct() {
        Mono<Product> operation=repository
                .save(product("WEATHER-01","Weather Shield","WEATHER","ACTIVE"))
                .flatMap(saved->repository.findByIdAndStatus(saved.getId(),"ACTIVE"));

        StepVerifier.create(operation)
                .assertNext(found->{
                    assertThat(found.getId()).isNotNull();
                    assertThat(found.getProductCode()).isEqualTo("WEATHER-01");
                    assertThat(found.getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
    }

    @Test
    void findByIdAndStatus_whenStatusDoesNotMatch_shouldReturnEmpty() {
        Mono<Product> operation=repository
                .save(product("WEATHER-01","Weather Shield","WEATHER","DRAFT"))
                .flatMap(saved->repository.findByIdAndStatus(saved.getId(),"ACTIVE"));

        StepVerifier.create(operation).verifyComplete();
    }

    @Test
    void findByIdAndStatus_whenIdDoesNotExist_shouldReturnEmpty() {
        StepVerifier.create(repository.findByIdAndStatus(999999L,"ACTIVE"))
                .verifyComplete();
    }
}