package org.example.productservice.repository;

import org.example.productservice.model.Product;
import org.example.productservice.model.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@DataR2dbcTest
@ActiveProfiles("test")
public abstract class BaseRepositoryTest {
    @Autowired protected DatabaseClient databaseClient;
    @Autowired protected ProductRepository productRepository;

    @BeforeEach
    void cleanDatabase() {
        StepVerifier.create(
                deleteFrom("policies")
                        .then(deleteFrom("coverages"))
                        .then(deleteFrom("products"))
        ).verifyComplete();
    }

    protected Mono<Void> deleteFrom(String table) {
        return databaseClient.sql("DELETE FROM "+table)
                .fetch()
                .rowsUpdated()
                .then();
    }

    protected Product product(String code,String name,String category,String status) {
        LocalDateTime now=LocalDateTime.now();

        return Product.builder()
                .productCode(code)
                .name(name)
                .description(name+" description")
                .category(category)
                .status(status)
                .minimumSumInsured(new BigDecimal("10000.00"))
                .maximumSumInsured(new BigDecimal("500000.00"))
                .minimumTermMonths(3)
                .maximumTermMonths(24)
                .eligibilityDescription("Standard eligibility")
                .createdBy(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    protected Product activeProduct(String code,String name,String category) {
        return product(code,name,category,ProductStatus.ACTIVE.name());
    }
}