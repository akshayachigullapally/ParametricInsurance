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

@Component
@RequiredArgsConstructor
public class InternalPolicyHandler{
    private final ProductService productService;

    public Mono<ServerResponse> getEligiblePolicies(ServerRequest request){
        Long productId=parseLong(request.queryParam("productId").orElse(null),"productId");
        Long locationZoneId=parseLong(request.queryParam("locationZoneId").orElse(null),"locationZoneId");
        LocalDateTime eventStart=parseLocalDateTime(request.queryParam("eventStart").orElse(null),"eventStart");
        LocalDateTime eventEnd=parseLocalDateTime(request.queryParam("eventEnd").orElse(null),"eventEnd");
        Long triggerEventId=parseLong(request.queryParam("triggerEventId").orElse(null),"triggerEventId");

        if(productId==null||locationZoneId==null||
                eventStart==null||eventEnd==null||triggerEventId==null){
            return Mono.error(new ValidationException("All parameters required"));
        }

        return productService.getEligiblePoliciesForEvent(
                        productId,locationZoneId,eventStart,eventEnd,triggerEventId)
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

    private LocalDateTime parseLocalDateTime(String value,String name){
        try{
            return value!=null?LocalDateTime.parse(value):null;
        }catch(Exception e){
            throw new ValidationException("Invalid "+name);
        }
    }
}