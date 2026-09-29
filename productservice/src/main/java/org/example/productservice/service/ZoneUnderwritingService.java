package org.example.productservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.productservice.client.TriggerServiceClient;
import org.example.productservice.dto.InternalZoneResponse;
import org.example.productservice.dto.ZoneExposureResponse;
import org.example.productservice.exception.ConflictException;
import org.example.productservice.exception.DependentServiceException;
import org.example.productservice.exception.NotFoundException;
import org.example.productservice.model.Policy;
import org.example.productservice.model.PolicyStatus;
import org.example.productservice.repository.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Zone-level underwriting controls: no new policies while the zone is under a
 * weather alert, and the total sum insured of open (ACTIVE + PENDING) policies
 * may not exceed the zone's exposure cap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneUnderwritingService{
    private static final BigDecimal HUNDRED=BigDecimal.valueOf(100);

    private final PolicyRepository policyRepository;
    private final TriggerServiceClient triggerServiceClient;

    /**
     * Fails with a ConflictException when a policy of {@code sumInsured} cannot be
     * sold in the zone. Policies in {@code excludedPolicyIds} are left out of the
     * current exposure (the policy being approved, or the one being renewed).
     */
    public Mono<Void> checkCanSell(Long zoneId,BigDecimal sumInsured,Collection<Long> excludedPolicyIds){
        return zone(zoneId)
                .flatMap(zone->{
                    if(Boolean.TRUE.equals(zone.getWeatherAlert())){
                        return Mono.error(new ConflictException(
                                "New policies cannot be sold in "+zone.getZoneName()
                                        +" while a weather alert is active"
                                        +(zone.getAlertMessage()!=null?": "+zone.getAlertMessage():"")));
                    }
                    if(zone.getMaxExposure()==null){
                        return Mono.<Void>empty();
                    }
                    return openPolicies(zoneId)
                            .filter(policy->!excludedPolicyIds.contains(policy.getId()))
                            .map(Policy::getSumInsured)
                            .reduce(BigDecimal.ZERO,BigDecimal::add)
                            .flatMap(used->{
                                BigDecimal after=used.add(sumInsured);
                                if(after.compareTo(zone.getMaxExposure())>0){
                                    BigDecimal remaining=zone.getMaxExposure().subtract(used).max(BigDecimal.ZERO);
                                    log.info("Exposure cap blocks sale in zone {}: used={} requested={} cap={}",
                                            zoneId,used,sumInsured,zone.getMaxExposure());
                                    return Mono.error(new ConflictException(
                                            "Exposure cap reached for "+zone.getZoneName()
                                                    +": ₹"+plain(used)+" of ₹"+plain(zone.getMaxExposure())
                                                    +" is already committed, only ₹"+plain(remaining)
                                                    +" of cover is still available"));
                                }
                                return Mono.<Void>empty();
                            });
                });
    }

    public Mono<List<ZoneExposureResponse>> exposureReport(){
        return triggerServiceClient.getActiveZones()
                .onErrorMap(error->!(error instanceof DependentServiceException),
                        error->new DependentServiceException("Trigger service is currently unavailable"))
                .concatMap(this::exposureOf)
                .collectList();
    }

    private Mono<ZoneExposureResponse> exposureOf(InternalZoneResponse zone){
        Mono<List<Policy>> active=policyRepository
                .findAllByLocationZoneIdAndStatus(zone.getZoneId(),PolicyStatus.ACTIVE.name()).collectList();
        Mono<List<Policy>> pending=policyRepository
                .findAllByLocationZoneIdAndStatus(zone.getZoneId(),PolicyStatus.PENDING.name()).collectList();

        return Mono.zip(active,pending).map(tuple->{
            BigDecimal activeExposure=sum(tuple.getT1());
            BigDecimal pendingExposure=sum(tuple.getT2());
            BigDecimal used=activeExposure.add(pendingExposure);
            BigDecimal cap=zone.getMaxExposure();
            boolean alert=Boolean.TRUE.equals(zone.getWeatherAlert());
            return ZoneExposureResponse.builder()
                    .zoneId(zone.getZoneId())
                    .zoneCode(zone.getZoneCode())
                    .zoneName(zone.getZoneName())
                    .weatherAlert(alert)
                    .alertMessage(zone.getAlertMessage())
                    .alertRaisedAt(zone.getAlertRaisedAt())
                    .maxExposure(cap)
                    .activeExposure(activeExposure)
                    .pendingExposure(pendingExposure)
                    .usedExposure(used)
                    .remainingExposure(cap==null?null:cap.subtract(used).max(BigDecimal.ZERO))
                    .utilizationPercent(cap==null||cap.signum()==0
                            ?null
                            :used.multiply(HUNDRED).divide(cap,1,RoundingMode.HALF_UP))
                    .activePolicyCount(tuple.getT1().size())
                    .pendingPolicyCount(tuple.getT2().size())
                    .salesOpen(!alert&&(cap==null||used.compareTo(cap)<0))
                    .build();
        });
    }

    private Mono<InternalZoneResponse> zone(Long zoneId){
        return triggerServiceClient.getZoneById(zoneId)
                .onErrorMap(WebClientResponseException.NotFound.class,
                        error->new NotFoundException("Location zone not found"))
                .onErrorMap(error->!(error instanceof NotFoundException),
                        error->new DependentServiceException("Trigger service is currently unavailable"))
                .switchIfEmpty(Mono.error(new NotFoundException("Location zone not found")));
    }

    private Flux<Policy> openPolicies(Long zoneId){
        return Flux.merge(
                policyRepository.findAllByLocationZoneIdAndStatus(zoneId,PolicyStatus.ACTIVE.name()),
                policyRepository.findAllByLocationZoneIdAndStatus(zoneId,PolicyStatus.PENDING.name()));
    }

    private static BigDecimal sum(List<Policy> policies){
        return policies.stream()
                .map(Policy::getSumInsured)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO,BigDecimal::add);
    }

    private static String plain(BigDecimal value){
        return value.stripTrailingZeros().toPlainString();
    }
}
