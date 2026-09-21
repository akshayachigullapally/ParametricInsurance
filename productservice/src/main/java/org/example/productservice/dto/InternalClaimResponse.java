package org.example.claimsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalClaimResponse {
    private Long claimId;
    private String claimNumber;
    private Long customerId;
    private Long policyId;
    private String policyNumber;
    private Long productId;
    private Long triggerEventId;
    private String status;
    private BigDecimal approvedAmount;
    private String claimSource;
    private Boolean manualReviewRequired;
}