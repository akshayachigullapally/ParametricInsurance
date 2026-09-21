package org.example.financialservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.model.PayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutResponse{
    private Long id;
    private String payoutReference;
    private Long customerId;
    private Long claimId;
    private String claimNumber;
    private Long policyId;
    private Long triggerEventId;
    private BigDecimal amount;
    private PayoutStatus status;
    private String externalReference;
    private String failureReason;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private LocalDateTime updatedAt;
}