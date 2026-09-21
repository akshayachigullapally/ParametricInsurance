package org.example.financialservice.dto;

import org.example.financialservice.model.PricingRuleStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePricingRuleStatusRequest{
    @NotNull
    private PricingRuleStatus status;
}