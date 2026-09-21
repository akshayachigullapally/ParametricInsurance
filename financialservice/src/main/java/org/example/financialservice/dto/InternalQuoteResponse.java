package org.example.financialservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.model.QuoteStatus;
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
    private QuoteStatus status;
    private LocalDateTime expiresAt;
    private Long policyId;
    private String policyNumber;
    private LocalDateTime consumedAt;
    private boolean valid;
    private String validationReason;
}