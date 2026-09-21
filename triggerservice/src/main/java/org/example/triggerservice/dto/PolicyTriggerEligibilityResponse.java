package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyTriggerEligibilityResponse {
    private Long policyId;
    private String policyNumber;
    private Long customerId;
    private Long productId;
    private String productCode;
    private String productName;
    private String policyStatus;

    private boolean eligible;
    private String eligibilityReason;

    private Long triggerEventId;
    private String triggerEventReference;
    private Long triggerDefinitionId;
    private Long perilId;
    private Long locationZoneId;

    private String measurementType;
    private BigDecimal aggregatedValue;
    private BigDecimal thresholdValue;
    private LocalDateTime eventWindowStart;
    private LocalDateTime eventWindowEnd;
    private LocalDateTime verifiedAt;
}