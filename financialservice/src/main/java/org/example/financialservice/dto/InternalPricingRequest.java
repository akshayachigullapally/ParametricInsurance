package org.example.financialservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalPricingRequest{
    private Long productId;
    private Long locationZoneId;
    private BigDecimal sumInsured;
    private Integer termMonths;
}
