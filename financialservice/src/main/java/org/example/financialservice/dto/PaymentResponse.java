package org.example.financialservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.model.PaymentMethod;
import org.example.financialservice.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse{
    private Long id;
    private String paymentReference;
    private Long customerId;
    private Long policyId;
    private String policyNumber;
    private Long quoteId;
    private BigDecimal amount;
    private PaymentMethod method;
    private PaymentStatus status;
    private String externalReference;
    private String failureReason;
    private Integer attemptNumber;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}