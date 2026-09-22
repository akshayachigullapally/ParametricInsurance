package org.example.productservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.productservice.dto.EligiblePolicyResponse;
import org.example.productservice.exception.ValidationException;
import org.example.productservice.service.ProductService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class InternalPolicyHandler{
    private final ProductService productService;

    public Mono<ServerResponse> getEligiblePolicies(ServerRequest request){
        Long productId=parseLong(request.queryParam("productId").orElse(null),"productId");
        Long locationZoneId=parseLong(request.queryParam("locationZoneId").orElse(null),"locationZoneId");

        if(productId==null||locationZoneId==null){
            return Mono.error(new ValidationException("productId and locationZoneId are required"));
        }

        return productService.getEligiblePoliciesForEvent(
                        productId,locationZoneId,
                        LocalDate.now().atStartOfDay(),
                        LocalDate.now().plusDays(1).atStartOfDay(),
                        null)
                .collectList()
                .flatMap(list->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list));
    }

    private Long parseLong(String value,String name){
        try{
            return value!=null?Long.parseLong(value):null;
        }catch(NumberFormatException e){
            throw new ValidationException("Invalid "+name);
        }
    }

}