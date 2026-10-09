package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.InternalPolicyResponse;
import org.example.triggerservice.dto.PolicyTriggerEligibilityResponse;
import org.example.triggerservice.exception.DependentServiceException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.ComparisonOperator;
import org.example.triggerservice.model.TriggerEvent;
import org.example.triggerservice.model.TriggerEventStatus;
import org.example.triggerservice.repository.TriggerEventRepository;
import org.example.triggerservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyTriggerEligibilityService{
    private final WebClient.Builder loadBalancedWebClientBuilder;
    private final TriggerEventRepository triggerEventRepository;

    @Value("${gateway.internal-key:climatesure-local-gateway-key}")
    private String internalKey;

    public Mono<PolicyTriggerEligibilityResponse> checkEligibility(Long policyId){
        log.debug("Check eligibility [policy trigger eligibility]: policyId={}",policyId);
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
                            .map(event->{
                                if(!satisfiesSnapshot(policy,event)){
                                    return buildIneligibleResponse(
                                            policy,
                                            "Event does not satisfy the policy's captured trigger snapshot");
                                }

                                return buildEligibleResponse(policy,event);
                            })
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

    private boolean satisfiesSnapshot(
            InternalPolicyResponse policy,
            TriggerEvent event
    ){
        String snapshotType=normalizeSnapshotType(
                policy.getSnapshotMeasurementType());
        if(StringUtils.hasText(snapshotType)){
            String eventType=event.getMeasurementType()!=null
                    ?normalizeSnapshotType(event.getMeasurementType().name())
                    :null;

            if(eventType==null||!snapshotType.equals(eventType)){
                return false;
            }
        }

        if(policy.getSnapshotThresholdValue()==null){
            return true;
        }

        if(event.getAggregatedValue()==null){
            return false;
        }

        ComparisonOperator operator=resolveSnapshotOperator(
                policy.getSnapshotComparisonOperator());

        if(operator==null){
            return true;
        }

        int comparison=event.getAggregatedValue()
                .compareTo(policy.getSnapshotThresholdValue());

        return switch(operator){
            case GREATER_THAN->comparison>0;
            case GREATER_THAN_OR_EQUAL->comparison>=0;
            case LESS_THAN->comparison<0;
            case LESS_THAN_OR_EQUAL->comparison<=0;
            case EQUAL->comparison==0;
        };
    }

    private String normalizeSnapshotType(String value){
        if(!StringUtils.hasText(value)){
            return null;
        }
        if("WATER_LEVEL".equalsIgnoreCase(value.trim())){
            return "RIVER_LEVEL";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private ComparisonOperator resolveSnapshotOperator(
            String snapshotOperator
    ){
        if(StringUtils.hasText(snapshotOperator)){
            try{
                return ComparisonOperator.valueOf(
                        snapshotOperator.trim().toUpperCase(Locale.ROOT));
            }catch(IllegalArgumentException exception){
                return null;
            }
        }
        return null;
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