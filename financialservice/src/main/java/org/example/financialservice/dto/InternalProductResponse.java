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
public class InternalProductResponse {
    private Long productId;
    private String productCode;
    private String status;
    private BigDecimal minSumInsured;
    private BigDecimal maxSumInsured;
    private Integer minTermMonths;
    private Integer maxTermMonths;
    private Boolean onlineSale;
}