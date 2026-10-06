package org.example.productservice.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.example.productservice.dto.InternalProductResponse;
import org.example.productservice.dto.MeasurementStatusResponse;
import org.example.productservice.dto.ProductRequest;
import org.example.productservice.dto.ProductResponse;
import org.example.productservice.dto.ProductThresholdHistoryResponse;
import org.example.productservice.dto.ProductThresholdUpdateRequest;
import org.example.productservice.dto.ProductThresholdUpdateResponse;
import org.example.productservice.dto.ProductTriggerSyncResponse;
import org.example.productservice.dto.UpdateProductStatusRequest;
import org.example.productservice.dto.VerifiedEventRequest;
import org.example.productservice.exception.ValidationException;
import org.example.productservice.service.ProductService;
import org.example.productservice.util.SecurityUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductHandler{
    private final ProductService productService;
    private final Validator validator;

    public Mono<ServerResponse> createProduct(ServerRequest request){
        return SecurityUtil.getUserId(request)
                .flatMap(adminId->readAndValidate(request,ProductRequest.class)
                        .flatMap(body->productService.createProduct(adminId,body)))
                .flatMap(response->ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> updateProduct(ServerRequest request){
        return parseProductId(request)
                .flatMap(productId->readAndValidate(request,ProductRequest.class)
                        .flatMap(body->productService.updateProduct(productId,body)))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getProducts(ServerRequest request){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(productService.getActiveProducts(),ProductResponse.class);
    }

    public Mono<ServerResponse> getProduct(ServerRequest request){
        return parseProductId(request)
                .flatMap(productService::getActiveProduct)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getByCategory(ServerRequest request){
        String category=request.pathVariable("category");

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(productService.getProductsByCategory(category),ProductResponse.class);
    }

    public Mono<ServerResponse> getAllProducts(ServerRequest request){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(productService.getAllProducts(),ProductResponse.class);
    }

    public Mono<ServerResponse> updateStatus(ServerRequest request){
        return parseProductId(request)
                .flatMap(productId->readAndValidate(request,UpdateProductStatusRequest.class)
                        .flatMap(body->productService.updateStatus(productId,body)))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> deleteProduct(ServerRequest request){
        return parseProductId(request)
                .flatMap(productService::deleteProduct)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    private <T> Mono<T> readAndValidate(ServerRequest request,Class<T> type){
        return request.bodyToMono(type)
                .switchIfEmpty(Mono.error(new ValidationException("Request body is required")))
                .flatMap(this::validate);
    }

    private <T> Mono<T> validate(T body){
        List<String> errors=validator.validate(body).stream()
                .map(this::message)
                .toList();

        return errors.isEmpty()
                ?Mono.just(body)
                :Mono.error(new ValidationException(errors));
    }

    private String message(ConstraintViolation<?> violation){
        String field=violation.getPropertyPath().toString();
        return field.isBlank()
                ?violation.getMessage()
                :field+": "+violation.getMessage();
    }

    private Mono<Long> parseProductId(ServerRequest request){
        try{
            Long id=Long.valueOf(request.pathVariable("productId"));

            return id>0
                    ?Mono.just(id)
                    :Mono.error(new ValidationException("Product id must be positive"));
        }catch(NumberFormatException e){
            return Mono.error(new ValidationException("Invalid product id"));
        }
    }
    public Mono<ServerResponse> getAdminProduct(ServerRequest request){
        return parseProductId(request)
                .flatMap(productService::getProduct)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getInternalProduct(ServerRequest request){
        return parseProductId(request)
                .flatMap(productService::getInternalProduct)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getInternalProducts(ServerRequest request){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(productService.getAllInternalProducts(),InternalProductResponse.class);
    }

    public Mono<ServerResponse> getInternalProductByCode(ServerRequest request){
        String productCode=request.pathVariable("productCode");

        return productService.getInternalProductByCode(productCode)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> updateThreshold(ServerRequest request){
        return SecurityUtil.getUserId(request)
                .flatMap(adminId->parseProductId(request)
                        .flatMap(productId->readAndValidate(
                                request,
                                ProductThresholdUpdateRequest.class
                        ).flatMap(body->productService.updateThreshold(
                                adminId,
                                productId,
                                body
                        ))))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getThresholdHistory(ServerRequest request){
        return parseProductId(request)
                .flatMapMany(productService::getThresholdHistory)
                .collectList()
                .flatMap(history->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(history));
    }

    public Mono<ServerResponse> syncTrigger(ServerRequest request){
        return SecurityUtil.getUserId(request)
                .flatMap(adminId->parseProductId(request)
                        .flatMap(productId->productService.syncTrigger(
                                adminId,
                                productId
                        )))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getMeasurementStatus(ServerRequest request){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(productService.getMeasurementStatus(),
                        MeasurementStatusResponse.class);
    }

    public Mono<ServerResponse> getPublicMeasurementStatus(ServerRequest request){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(productService.getPublicMeasurementStatus(),
                        MeasurementStatusResponse.class);
    }

    public Mono<ServerResponse> processVerifiedEvent(ServerRequest request){
        return request.bodyToMono(VerifiedEventRequest.class)
                .switchIfEmpty(Mono.error(new org.example.productservice.exception.ValidationException(
                        "Request body is required"
                )))
                .flatMap(this::validate)
                .flatMap(productService::processVerifiedEvent)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }
}