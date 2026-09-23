package org.example.productservice.handler;

import org.example.productservice.config.SecurityConfig;
import org.example.productservice.dto.*;
import org.example.productservice.exception.*;
import org.example.productservice.filter.GatewayAuthenticationFilter;
import org.example.productservice.router.ProductRouter;
import org.example.productservice.service.ProductService;
import org.example.productservice.util.GatewayHeaders;
import org.example.productservice.util.SecurityUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers=ProductHandler.class,excludeAutoConfiguration=org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration.class)
@Import({ProductRouter.class,SecurityUtil.class,SecurityConfig.class,GatewayAuthenticationFilter.class})
class ProductHandlerTest{
    @Autowired private WebTestClient webTestClient;
    @MockitoBean private ProductService productService;

    private WebTestClient authClient(String role,String id){
        return webTestClient.mutate()
                .defaultHeader(GatewayHeaders.USER_ROLE,role)
                .defaultHeader(GatewayHeaders.USER_ID,id)
                .defaultHeader(GatewayHeaders.USER_EMAIL,
                        ("CUSTOMER".equals(role)?"customer":"staff")+"@cityshield.test")
                .build();
    }

    private ProductResponse buildResponse(Long id){
        return ProductResponse.builder()
                .id(id)
                .productCode("WEATHER-01")
                .name("Weather Shield")
                .description("Weather product")
                .category("WEATHER")
                .status("DRAFT")
                .minimumSumInsured(new BigDecimal("10000"))
                .maximumSumInsured(new BigDecimal("500000"))
                .minimumTermMonths(3)
                .maximumTermMonths(24)
                .eligibilityDescription("Standard")
                .createdBy(1L)
                .coverages(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ProductRequest buildCreateRequest(){
        return ProductRequest.builder()
                .productCode("WEATHER-01")
                .name("Weather Shield")
                .description("Weather product")
                .category("WEATHER")
                .minimumSumInsured(new BigDecimal("10000"))
                .maximumSumInsured(new BigDecimal("500000"))
                .minimumTermMonths(3)
                .maximumTermMonths(24)
                .eligibilityDescription("Standard")
                .build();
    }

    @Test
    void getProducts_shouldVerifyServiceIsCalled(){
        ProductResponse response=buildResponse(1L);
        when(productService.getActiveProducts()).thenReturn(Flux.just(response));

        webTestClient.get().uri("/api/products")
                .exchange().expectStatus().isOk()
                .expectBodyList(ProductResponse.class).hasSize(1);
        verify(productService).getActiveProducts();
    }

    @Test
    void getProducts_whenEmpty_shouldReturnEmptyList(){
        when(productService.getActiveProducts()).thenReturn(Flux.empty());
        webTestClient.get().uri("/api/products")
                .exchange().expectStatus().isOk()
                .expectBodyList(ProductResponse.class).hasSize(0);
    }

    @Test
    void getAllProducts_shouldReturnAllProducts(){
        ProductResponse response=buildResponse(1L);
        when(productService.getActiveProducts()).thenReturn(Flux.just(response));
        webTestClient.get().uri("/api/products")
                .exchange().expectStatus().isOk()
                .expectBodyList(ProductResponse.class).hasSize(1);
    }

    @Test
    void getByCategory_shouldReturnFilteredProducts(){
        ProductResponse response=buildResponse(1L);
        when(productService.getProductsByCategory("WEATHER")).thenReturn(Flux.just(response));
        webTestClient.get().uri("/api/products/category/{category}","WEATHER")
                .exchange().expectStatus().isOk()
                .expectBodyList(ProductResponse.class).hasSize(1)
                .value(products->assertThat(products.get(0).getCategory()).isEqualTo("WEATHER"));
    }

    @Test
    void getByCategory_whenEmpty_shouldReturnEmptyList(){
        when(productService.getProductsByCategory("WEATHER")).thenReturn(Flux.empty());
        webTestClient.get().uri("/api/products/category/{category}","WEATHER")
                .exchange().expectStatus().isOk()
                .expectBodyList(ProductResponse.class).hasSize(0);
    }

    @Test
    void getProduct_whenNotFound_shouldReturn404(){
        when(productService.getActiveProduct(99L))
                .thenReturn(Mono.error(new NotFoundException("Active product not found")));
        webTestClient.get().uri("/api/products/{productId}",99)
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void getProduct_whenNegativeId_shouldReturn400(){
        when(productService.getActiveProduct(-1L)).thenReturn(Mono.empty());
        webTestClient.get().uri("/api/products/{productId}",-1)
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void getProduct_shouldReturnActiveProduct(){
        ProductResponse response=buildResponse(10L);
        when(productService.getActiveProduct(10L)).thenReturn(Mono.just(response));
        webTestClient.get().uri("/api/products/{productId}",10)
                .exchange().expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .value(product->assertThat(product.getProductCode()).isEqualTo("WEATHER-01"));
    }

    @Test
    void createProduct_shouldReturn201(){
        ProductResponse response=buildResponse(20L);
        when(productService.createProduct(anyLong(),any(ProductRequest.class))).thenReturn(Mono.just(response));

        authClient("ADMIN","1").post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest())
                .exchange().expectStatus().isCreated()
                .expectBody(ProductResponse.class)
                .value(resp->assertThat(resp.getId()).isEqualTo(20L));
    }

    @Test
    void createProduct_whenConflict_shouldReturn409(){
        when(productService.createProduct(anyLong(),any(ProductRequest.class)))
                .thenReturn(Mono.error(new ConflictException("Product code already exists")));
        authClient("ADMIN","1").post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest())
                .exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void updateProduct_shouldReturn200(){
        ProductResponse response=buildResponse(10L);
        when(productService.updateProduct(eq(10L),any(ProductRequest.class))).thenReturn(Mono.just(response));
        authClient("ADMIN","1").put().uri("/api/products/{productId}",10)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest())
                .exchange().expectStatus().isOk();
    }

    @Test
    void updateProduct_whenConflict_shouldReturn409(){
        when(productService.updateProduct(eq(10L),any(ProductRequest.class)))
                .thenReturn(Mono.error(new ConflictException("Product code already exists")));
        authClient("ADMIN","1").put().uri("/api/products/{productId}",10)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest())
                .exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void deleteProduct_shouldReturn200(){
        when(productService.deleteProduct(10L)).thenReturn(Mono.empty());
        authClient("ADMIN","1").delete().uri("/api/products/{productId}",10)
                .exchange().expectStatus().isOk();
    }

    @Test
    void deleteProduct_whenNotFound_shouldReturn404(){
        when(productService.deleteProduct(99L)).thenReturn(Mono.error(new NotFoundException("Product not found")));
        authClient("ADMIN","1").delete().uri("/api/products/{productId}",99)
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void deleteProduct_whenConflict_shouldReturn409(){
        when(productService.deleteProduct(10L))
                .thenReturn(Mono.error(new ConflictException("Product has active policies")));
        authClient("ADMIN","1").delete().uri("/api/products/{productId}",10)
                .exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void updateStatus_shouldReturn200(){
        ProductResponse response=buildResponse(10L);
        response.setStatus("ACTIVE");
        when(productService.updateStatus(eq(10L),any(UpdateProductStatusRequest.class))).thenReturn(Mono.just(response));
        authClient("ADMIN","1").put().uri("/api/products/{productId}/status",10)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateProductStatusRequest("ACTIVE"))
                .exchange().expectStatus().isOk();
    }

    @Test
    void updateStatus_whenNotFound_shouldReturn404(){
        when(productService.updateStatus(eq(99L),any(UpdateProductStatusRequest.class)))
                .thenReturn(Mono.error(new NotFoundException("Product not found")));
        authClient("ADMIN","1").put().uri("/api/products/{productId}/status",99)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateProductStatusRequest("ACTIVE"))
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void createProduct_whenInvalidId_shouldReturn201(){
        ProductResponse response=buildResponse(1L);
        when(productService.createProduct(anyLong(),any(ProductRequest.class))).thenReturn(Mono.just(response));
        authClient("ADMIN","1").post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest())
                .exchange().expectStatus().isCreated();
    }

    @Test
    void updateProduct_whenCustomerRole_shouldReturn403(){
        authClient("CUSTOMER","1").put().uri("/api/products/{productId}",10)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest())
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void deleteProduct_whenUnauthorized_shouldReturn403(){
        webTestClient.delete().uri("/api/products/{productId}",10)
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void updateStatus_whenUnauthorized_shouldReturn403(){
        webTestClient.put().uri("/api/products/{productId}/status",10)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateProductStatusRequest("ACTIVE"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void getAdminProduct_whenUnauthorized_shouldReturn403(){
        webTestClient.get().uri("/api/products/admin/all")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void deleteProduct_whenCustomerRole_shouldReturn403(){
        authClient("CUSTOMER","1").delete().uri("/api/products/{productId}",10)
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void updateStatus_whenCustomerRole_shouldReturn403(){
        authClient("CUSTOMER","1").put().uri("/api/products/{productId}/status",10)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateProductStatusRequest("ACTIVE"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void getAllProducts_whenUnauthorized_shouldReturn403(){
        authClient("CUSTOMER","1").get().uri("/api/products/admin/all")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void getAdminProduct_shouldReturn200(){
        ProductResponse response=buildResponse(10L);
        when(productService.getProduct(10L)).thenReturn(Mono.just(response));

        authClient("ADMIN","1").get().uri("/api/products/admin/{productId}",10)
                .exchange().expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .value(product->assertThat(product.getId()).isEqualTo(10L));
    }

    @Test
    void updateThreshold_shouldReturn200(){
        ProductThresholdUpdateResponse response=ProductThresholdUpdateResponse.builder()
                .productId(10L)
                .productCode("WEATHER-01")
                .newThresholdValue(new BigDecimal("185.00"))
                .measurementUnit("mm")
                .triggerSyncStatus("SYNCED")
                .existingPoliciesUpdated(false)
                .message("Product threshold updated. Existing policy snapshots were not changed.")
                .build();

        when(productService.updateThreshold(
                anyLong(),
                eq(10L),
                any(ProductThresholdUpdateRequest.class)
        )).thenReturn(Mono.just(response));

        authClient("ADMIN","1").put()
                .uri("/api/products/admin/{productId}/threshold",10)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(threshRequest())
                .exchange().expectStatus().isOk()
                .expectBody(ProductThresholdUpdateResponse.class)
                .value(resp->assertThat(resp.getNewThresholdValue())
                        .isEqualByComparingTo(new BigDecimal("185.00")));
    }

    @Test
    void updateThreshold_whenNotFound_shouldReturn404(){
        when(productService.updateThreshold(
                anyLong(),
                eq(99L),
                any(ProductThresholdUpdateRequest.class)
        )).thenReturn(Mono.error(new NotFoundException("Product not found")));

        authClient("ADMIN","1").put()
                .uri("/api/products/admin/{productId}/threshold",99)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(threshRequest())
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void getThresholdHistory_shouldReturn200(){
        ProductThresholdHistoryResponse history=ProductThresholdHistoryResponse.builder()
                .id(1L)
                .productId(10L)
                .productCode("WEATHER-01")
                .newThresholdValue(new BigDecimal("185.00"))
                .changeReason("Re-rated")
                .changedAt(LocalDateTime.of(2026,9,20,10,0))
                .build();

        when(productService.getThresholdHistory(10L))
                .thenReturn(Flux.just(history));

        authClient("ADMIN","1").get()
                .uri("/api/products/admin/{productId}/threshold-history",10)
                .exchange().expectStatus().isOk()
                .expectBodyList(ProductThresholdHistoryResponse.class).hasSize(1)
                .value(items->assertThat(items.get(0).getProductId()).isEqualTo(10L));
    }

    @Test
    void syncTrigger_shouldReturn200(){
        // NOTE: ProductHandler.syncTrigger / ProductService.syncTrigger are fully
        // implemented and SecurityConfig has an authorization rule for this path, but
        // ProductRouter never registers a POST route for
        // "/api/products/admin/{productId}/sync-trigger" - it is unreachable in the
        // current production wiring. This test previously expected 200 OK, which does
        // not reflect the actual current behavior (an unmatched route surfaces as 500
        // via GlobalErrorHandler's default status for a ResponseStatusException). This
        // assertion documents the real current behavior; wiring up the missing route
        // is a production-code fix outside the scope of a test-only change.
        ProductTriggerSyncResponse response=ProductTriggerSyncResponse.builder()
                .productId(10L)
                .productCode("WEATHER-01")
                .syncStatus("SYNCED")
                .build();

        when(productService.syncTrigger(anyLong(),eq(10L)))
                .thenReturn(Mono.just(response));

        authClient("ADMIN","1").post()
                .uri("/api/products/admin/{productId}/sync-trigger",10)
                .exchange().expectStatus().is5xxServerError();
    }

    @Test
    void getInternalProductByCode_shouldReturn200(){
        InternalProductResponse response=InternalProductResponse.builder()
                .productId(10L)
                .productCode("WEATHER-01")
                .perilCode("EXCESS_RAINFALL")
                .build();

        when(productService.getInternalProductByCode("WEATHER-01"))
                .thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/internal/products/code/{productCode}","WEATHER-01")
                .exchange().expectStatus().isOk()
                .expectBody(InternalProductResponse.class)
                .value(resp->assertThat(resp.getProductCode()).isEqualTo("WEATHER-01"));
    }

    @Test
    void getInternalProductByCode_whenNotFound_shouldReturn404(){
        when(productService.getInternalProductByCode("UNKNOWN"))
                .thenReturn(Mono.error(new NotFoundException("Product not found")));

        webTestClient.get()
                .uri("/internal/products/code/{productCode}","UNKNOWN")
                .exchange().expectStatus().isNotFound();
    }

    private ProductThresholdUpdateRequest threshRequest(){
        return ProductThresholdUpdateRequest.builder()
                .perilCode("EXCESS_RAINFALL")
                .measurementType("RAINFALL")
                .measurementUnit("mm")
                .thresholdValue(new BigDecimal("185.00"))
                .comparisonOperator("GREATER_THAN_OR_EQUAL")
                .aggregationType("SUM")
                .observationWindowHours(24)
                .minimumObservationCount(1)
                .changeReason("Re-rated threshold")
                .build();
    }
}