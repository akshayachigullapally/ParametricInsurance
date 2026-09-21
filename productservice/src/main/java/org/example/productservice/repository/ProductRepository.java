package org.example.productservice.repository;

import org.example.productservice.model.Product;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;

public interface ProductRepository extends ReactiveCrudRepository<Product,Long>{
    Mono<Product> findByProductCodeIgnoreCase(String productCode);
    Mono<Boolean> existsByProductCodeIgnoreCase(String productCode);
    Flux<Product> findAllByStatus(String status);
    Flux<Product> findAllByCategoryAndStatus(String category,String status);
    Mono<Product> findByIdAndStatus(Long id,String status);
}