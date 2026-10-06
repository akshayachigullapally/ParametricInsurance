package org.example.claimsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimCreationRequest {
    private Long triggerEventId;
    private String triggerEventReference;
    private Long triggerDefinitionId;
    private Long productId;
    private Long locationZoneId;
    private Long perilId;
    private String measurementType;
    private String measurementUnit;
    private BigDecimal aggregatedValue;
    private BigDecimal thresholdValue;
    /** Sent instead of aggregatedValue by the current-measurement flow. */
    private BigDecimal measuredValue;
    private String comparisonOperator;
    private LocalDateTime eventWindowStart;
    private LocalDateTime eventWindowEnd;
    private List<EligiblePolicyInfo> eligiblePolicies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EligiblePolicyInfo {
        private Long policyId;
        private String policyNumber;
        private Long customerId;
        private BigDecimal sumInsured;
    }
}