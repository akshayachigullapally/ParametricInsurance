package org.example.triggerservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentMeasurementResult{
    private boolean matched;
    private Long productId;
    private String productCode;
    private String productName;
    private Long locationZoneId;
    private String measurementType;
    private String measurementUnit;
    private BigDecimal currentValue;
    private BigDecimal currentProductThresholdValue;
    private String comparisonOperator;
    private Long triggerEventId;
    private String triggerEventReference;
    private String triggerEventStatus;
    private int activePolicyCount;
    private int matchedPolicyCount;
    private int claimsCreatedCount;
    private int existingClaimsCount;
    private int notificationsCreatedCount;
    private String message;
}
