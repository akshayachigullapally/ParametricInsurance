package org.example.productservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductThresholdUpdateResponse{
    private Long productId;
    private String productCode;
    private BigDecimal previousThresholdValue;
    private BigDecimal newThresholdValue;
    private String measurementUnit;
    private String triggerDescription;
    private String triggerSyncStatus;
    private Boolean existingPoliciesUpdated;
    private String message;
}