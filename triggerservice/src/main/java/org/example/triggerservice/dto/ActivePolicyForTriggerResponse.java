package org.example.triggerservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivePolicyForTriggerResponse{
    private Long policyId;
    private String policyNumber;
    private Long customerId;
    private Long productId;
    private Long locationZoneId;
    private BigDecimal sumInsured;
    private String status;
    private TriggerSnapshot triggerSnapshot;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerSnapshot{
        private String perilCode;
        private String measurementType;
        private String measurementUnit;
        private BigDecimal thresholdValue;
        private String comparisonOperator;
        private String triggerDescription;
    }
}
