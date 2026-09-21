package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.InternalPolicyResponse;
import org.example.triggerservice.dto.PolicyTriggerEligibilityResponse;
import org.example.triggerservice.exception.DependentServiceException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.TriggerEvent;
import org.example.triggerservice.model.TriggerEventStatus;
import org.example.triggerservice.repository.TriggerEventRepository;
import org.example.triggerservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PolicyTriggerEligibilityService{
    private final WebClient.Builder loadBalancedWebClientBuilder;
    private final TriggerEventRepository triggerEventRepository;

    @Value("${gateway.internal-key}")
    private String internalKey;

    public Mono<PolicyTriggerEligibilityResponse> checkEligibility(Long policyId){
        validateId(policyId,"Policy ID");

        return getPolicy(policyId)
                .flatMap(policy->{
                    if(policy.getId()==null){
                        return Mono.error(new DependentServiceException(
                                "Product service returned incomplete policy information"
                        ));
                    }

                    if(!"ACTIVE".equalsIgnoreCase(policy.getStatus())){
                        return Mono.just(buildIneligibleResponse(
                                policy,
                                "Policy is not ACTIVE"
                        ));
                    }

                    if(policy.getProductId()==null){
                        return Mono.just(buildIneligibleResponse(
                                policy,
                                "Policy product information is unavailable"
                        ));
                    }

                    if(policy.getLocationZoneId()==null){
                        return Mono.just(buildIneligibleResponse(
                                policy,
                                "Policy location zone information is unavailable"
                        ));
                    }

                    if(policy.getStartDate()==null||
                            policy.getEndDate()==null){
                        return Mono.just(buildIneligibleResponse(
                                policy,
                                "Policy coverage dates are unavailable"
                        ));
                    }

                    return findLatestApplicableEvent(policy)
                            .map(event->buildEligibleResponse(policy,event))
                            .defaultIfEmpty(buildIneligibleResponse(
                                    policy,
                                    "No verified trigger event applies to the policy product, location zone and coverage period"
                            ));
                });
    }

    private Mono<InternalPolicyResponse> getPolicy(Long policyId){
        return loadBalancedWebClientBuilder.build()
                .get()
                .uri(
                        "http://PRODUCT-SERVICE/internal/policies/{policyId}",
                        policyId
                )
                .header(GatewayHeaders.GATEWAY_KEY,internalKey)
                .retrieve()
                .onStatus(
                        status->status.value()==404,
                        response->Mono.error(
                                new NotFoundException("Policy not found")
                        )
                )
                .onStatus(
                        status->status.value()==401||
                                status.value()==403,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Product service rejected the internal policy request"
                                )
                        )
                )
                .onStatus(
                        status->status.is4xxClientError()&&
                                status.value()!=404&&
                                status.value()!=401&&
                                status.value()!=403,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Internal policy validation failed"
                                )
                        )
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Product service is currently unavailable"
                                )
                        )
                )
                .bodyToMono(InternalPolicyResponse.class)
                .switchIfEmpty(Mono.error(
                        new DependentServiceException(
                                "Product service returned an empty policy response"
                        )
                ))
                .onErrorMap(
                        WebClientRequestException.class,
                        exception->new DependentServiceException(
                                "Product service is currently unavailable"
                        )
                );
    }

    private Mono<TriggerEvent> findLatestApplicableEvent(
            InternalPolicyResponse policy
    ){
        return triggerEventRepository
                .findAllByProductIdAndStatusOrderByDetectedAtDesc(
                        policy.getProductId(),
                        TriggerEventStatus.VERIFIED
                )
                .filter(event->isApplicableEvent(event,policy))
                .next();
    }

    private boolean isApplicableEvent(
            TriggerEvent event,
            InternalPolicyResponse policy
    ){
        if(event.getProductId()==null||
                event.getLocationZoneId()==null||
                event.getWindowStart()==null||
                event.getWindowEnd()==null){
            return false;
        }

        if(!policy.getProductId().equals(event.getProductId())){
            return false;
        }

        if(!policy.getLocationZoneId().equals(
                event.getLocationZoneId()
        )){
            return false;
        }

        LocalDate eventStart=event.getWindowStart().toLocalDate();
        LocalDate eventEnd=event.getWindowEnd().toLocalDate();

        return !eventEnd.isBefore(policy.getStartDate())&&
                !eventStart.isAfter(policy.getEndDate());
    }

    private PolicyTriggerEligibilityResponse buildEligibleResponse(
            InternalPolicyResponse policy,
            TriggerEvent event
    ){
        return PolicyTriggerEligibilityResponse.builder()
                .policyId(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .customerId(policy.getCustomerId())
                .productId(policy.getProductId())
                .productCode(policy.getProductCode())
                .productName(policy.getProductName())
                .policyStatus(policy.getStatus())
                .eligible(true)
                .eligibilityReason(
                        "A verified trigger event applies to the policy product, location zone and coverage period"
                )
                .triggerEventId(event.getId())
                .triggerEventReference(event.getEventReference())
                .triggerDefinitionId(event.getTriggerDefinitionId())
                .perilId(event.getPerilId())
                .locationZoneId(event.getLocationZoneId())
                .measurementType(event.getMeasurementType().name())
                .aggregatedValue(event.getAggregatedValue())
                .thresholdValue(event.getThresholdValue())
                .eventWindowStart(event.getWindowStart())
                .eventWindowEnd(event.getWindowEnd())
                .verifiedAt(event.getReviewedAt())
                .build();
    }

    private PolicyTriggerEligibilityResponse buildIneligibleResponse(
            InternalPolicyResponse policy,
            String reason
    ){
        return PolicyTriggerEligibilityResponse.builder()
                .policyId(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .customerId(policy.getCustomerId())
                .productId(policy.getProductId())
                .productCode(policy.getProductCode())
                .productName(policy.getProductName())
                .policyStatus(policy.getStatus())
                .eligible(false)
                .eligibilityReason(reason)
                .build();
    }

    private void validateId(Long id,String fieldName){
        if(id==null||id<=0){
            throw new ValidationException(
                    fieldName+" must be greater than zero"
            );
        }
    }
}