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
public class QuoteResponse{
    private Long id;
    private String quoteReference;
    private Long customerId;
    private Long productId;
    private Long locationZoneId;
    private BigDecimal sumInsured;
    private Integer termMonths;
    private BigDecimal annualRate;
    private BigDecimal termFactor;
    private BigDecimal locationFactor;
    private BigDecimal premiumAmount;
    private QuoteStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private Long policyId;
    private String policyNumber;
    private LocalDateTime consumedAt;
}