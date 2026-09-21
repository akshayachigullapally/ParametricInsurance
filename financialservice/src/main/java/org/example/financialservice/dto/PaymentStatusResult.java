package org.example.financialservice.dto;

import org.example.financialservice.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResult{
    private Long policyId;
    private PaymentStatus latestStatus;
    private String paymentReference;
    private boolean successful;
}