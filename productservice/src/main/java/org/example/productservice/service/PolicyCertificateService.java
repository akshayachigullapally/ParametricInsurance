package org.example.productservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.productservice.client.TriggerServiceClient;
import org.example.productservice.dto.InternalZoneResponse;
import org.example.productservice.exception.ConflictException;
import org.example.productservice.exception.NotFoundException;
import org.example.productservice.model.Policy;
import org.example.productservice.model.PolicyStatus;
import org.example.productservice.model.PolicyTriggerSnapshot;
import org.example.productservice.pdf.SimplePdfDocument;
import org.example.productservice.repository.PolicyRepository;
import org.example.productservice.repository.PolicyTriggerSnapshotRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** PDF certificate of insurance for an issued (active or expired) policy. */
@Slf4j
@Service
public class PolicyCertificateService{
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Set<String> ISSUED=Set.of(
            PolicyStatus.ACTIVE.name(),
            PolicyStatus.EXPIRED.name()
    );

    private final PolicyRepository policyRepository;
    private final PolicyTriggerSnapshotRepository snapshotRepository;
    private final TriggerServiceClient triggerServiceClient;

    public PolicyCertificateService(PolicyRepository policyRepository,
                                    PolicyTriggerSnapshotRepository snapshotRepository,
                                    TriggerServiceClient triggerServiceClient){
        this.policyRepository=policyRepository;
        this.snapshotRepository=snapshotRepository;
        this.triggerServiceClient=triggerServiceClient;
    }

    public Mono<Certificate> forCustomer(Long customerId,Long policyId){
        log.debug("For customer [policy certificate]: customerId={}, policyId={}",customerId,policyId);
        return policyRepository.findByIdAndCustomerId(policyId,customerId)
                .switchIfEmpty(Mono.error(new NotFoundException("Policy not found")))
                .flatMap(this::render);
    }

    public Mono<Certificate> forStaff(Long policyId){
        log.debug("For staff [policy certificate]: policyId={}",policyId);
        return policyRepository.findById(policyId)
                .switchIfEmpty(Mono.error(new NotFoundException("Policy not found")))
                .flatMap(this::render);
    }

    private Mono<Certificate> render(Policy policy){
        if(!ISSUED.contains(policy.getStatus())){
            return Mono.error(new ConflictException(
                    "A certificate is available once the policy has been approved"));
        }

        Mono<Optional<PolicyTriggerSnapshot>> snapshot=snapshotRepository.findByPolicyId(policy.getId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        Mono<Optional<InternalZoneResponse>> zone=Mono.defer(()->triggerServiceClient
                        .getZoneById(policy.getLocationZoneId()))
                .map(Optional::of)
                .onErrorReturn(Optional.empty())
                .defaultIfEmpty(Optional.empty());

        return Mono.zip(snapshot,zone)
                .flatMap(tuple->Mono.fromCallable(()->build(policy,
                                tuple.getT1().orElse(null),
                                tuple.getT2().orElse(null)))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(bytes->new Certificate(policy.getPolicyNumber()+"-certificate.pdf",bytes));
    }

    private byte[] build(Policy policy,PolicyTriggerSnapshot snapshot,InternalZoneResponse zone){
        Map<String,String> policyRows=new LinkedHashMap<>();
        policyRows.put("Policy number",policy.getPolicyNumber());
        policyRows.put("Status",capitalize(policy.getStatus()));
        policyRows.put("Product",policy.getProductName()+" ("+policy.getProductCode()+")");
        policyRows.put("Policyholder (customer ID)",String.valueOf(policy.getCustomerId()));
        policyRows.put("Period of cover",
                (policy.getStartDate()!=null?policy.getStartDate().format(DATE):"-")+" to "
                        +(policy.getEndDate()!=null?policy.getEndDate().format(DATE):"-"));
        policyRows.put("Term",policy.getTermMonths()+(Integer.valueOf(1).equals(policy.getTermMonths())?" month":" months"));
        policyRows.put("Sum insured",money(policy.getSumInsured()));
        policyRows.put("Premium",money(policy.getPremiumAmount()));
        policyRows.put("Auto-renew",Boolean.TRUE.equals(policy.getAutoRenew())?"On":"Off");

        Map<String,String> zoneRows=new LinkedHashMap<>();
        if(zone!=null){
            zoneRows.put("Zone",zone.getZoneName()+" ("+zone.getZoneCode()+")");
            zoneRows.put("Location",join(zone.getDistrict(),zone.getState(),zone.getCountry()));
            if(zone.getRadiusKm()!=null){
                zoneRows.put("Radius",zone.getRadiusKm().stripTrailingZeros().toPlainString()+" km");
            }
        }else{
            zoneRows.put("Zone","#"+policy.getLocationZoneId());
        }

        SimplePdfDocument document=new SimplePdfDocument("Certificate of insurance",
                "Parametric cover issued by ClimateSure")
                .section("Policy",policyRows)
                .section("Insured location",zoneRows);

        if(snapshot!=null){
            Map<String,String> triggerRows=new LinkedHashMap<>();
            String unit=snapshot.getMeasurementUnit()!=null?" "+snapshot.getMeasurementUnit():"";
            triggerRows.put("Measurement",humanize(snapshot.getMeasurementType()));
            triggerRows.put("Pays when",aggregationText(snapshot.getAggregationType())
                    +humanize(snapshot.getMeasurementType()).toLowerCase(Locale.ROOT)+" "
                    +operatorText(snapshot.getComparisonOperator())+" "
                    +plain(snapshot.getThresholdValue())+unit);
            if(snapshot.getObservationWindowHours()!=null){
                triggerRows.put("Observation window",snapshot.getObservationWindowHours()+" hours");
            }
            if(snapshot.getTriggerDescription()!=null&&!snapshot.getTriggerDescription().isBlank()){
                triggerRows.put("Description",snapshot.getTriggerDescription());
            }
            document.section("Trigger condition (locked at purchase)",triggerRows);
        }

        return document
                .note("This is a parametric policy. The sum insured is paid when the trigger condition above "
                        +"is met in the insured zone during the period of cover, as verified by ClimateSure. "
                        +"No loss assessment is required.")
                .render();
    }

    private static String operatorText(String operator){
        if(operator==null){
            return ">=";
        }
        return switch(operator){
            case "GREATER_THAN" -> ">";
            case "GREATER_THAN_OR_EQUAL" -> ">=";
            case "LESS_THAN" -> "<";
            case "LESS_THAN_OR_EQUAL" -> "<=";
            case "EQUAL", "EQUALS" -> "=";
            default -> operator;
        };
    }

    private static String aggregationText(String aggregation){
        if(aggregation==null){
            return "";
        }
        return switch(aggregation){
            case "SUM" -> "Total ";
            case "MAX" -> "Highest ";
            case "MIN" -> "Lowest ";
            case "AVERAGE", "AVG" -> "Average ";
            default -> "";
        };
    }

    private static String humanize(String value){
        if(value==null||value.isBlank()){
            return "-";
        }
        return capitalize(value.replace('_',' '));
    }

    private static String capitalize(String value){
        if(value==null||value.isBlank()){
            return "-";
        }
        String lower=value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0))+lower.substring(1);
    }

    private static String plain(BigDecimal value){
        return value==null?"-":value.stripTrailingZeros().toPlainString();
    }

    private static String join(String... parts){
        StringBuilder out=new StringBuilder();
        for(String part:parts){
            if(part!=null&&!part.isBlank()){
                if(!out.isEmpty()){
                    out.append(", ");
                }
                out.append(part);
            }
        }
        return out.isEmpty()?"-":out.toString();
    }

    private static String money(BigDecimal amount){
        if(amount==null){
            return "-";
        }
        NumberFormat format=NumberFormat.getNumberInstance(new Locale("en","IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "INR "+format.format(amount);
    }

    public record Certificate(String fileName,byte[] bytes){}
}
