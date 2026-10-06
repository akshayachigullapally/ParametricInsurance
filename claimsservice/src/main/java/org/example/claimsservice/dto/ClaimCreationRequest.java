package org.example.claimsservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
    @NotNull(message="Trigger event id is required")
    @Positive(message="Trigger event id must be positive")
    private Long triggerEventId;
    private String triggerEventReference;
    private Long triggerDefinitionId;
    @Positive(message="Product id must be positive")
    private Long productId;
    @Positive(message="Location zone id must be positive")
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
    @NotEmpty(message="At least one matched policy is required")
    @Valid
    private List<EligiblePolicyInfo> eligiblePolicies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EligiblePolicyInfo {
        @NotNull(message="Policy id is required")
        @Positive(message="Policy id must be positive")
        private Long policyId;
        private String policyNumber;
        @NotNull(message="Customer id is required")
        @Positive(message="Customer id must be positive")
        private Long customerId;
        @NotNull(message="Sum insured is required")
        @Positive(message="Sum insured must be positive")
        private BigDecimal sumInsured;
    }
}