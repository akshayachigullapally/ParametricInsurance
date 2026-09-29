package org.example.productservice.dto;

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
public class EligiblePolicyResponse {
    private Long policyId;
    private String policyNumber;
    private Long customerId;
    private Long productId;
    private String productCode;
    private String productName;
    private Long locationZoneId;
    private BigDecimal sumInsured;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;
    /** True when the policy's latest premium payment succeeded; unpaid policies must not be paid out. */
    private Boolean premiumPaid;
    private TriggerSnapshotResponse triggerSnapshot;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerSnapshotResponse {
        private String measurementType;
        private String measurementUnit;
        private String aggregationType;
        private String comparisonOperator;
        private BigDecimal thresholdValue;
        private Integer observationWindowHours;
        private Integer minimumObservationCount;
    }
}