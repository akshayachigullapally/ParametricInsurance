package org.example.productservice.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalQuoteResponse{
    private Long quoteId;
    private String quoteReference;
    private Long customerId;
    private Long productId;
    private Long locationZoneId;
    private BigDecimal sumInsured;
    private Integer termMonths;
    private BigDecimal premiumAmount;
    private String status;
    private boolean valid;
    private String validationReason;
}
