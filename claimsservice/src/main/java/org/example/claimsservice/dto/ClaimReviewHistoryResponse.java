package org.example.claimsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One action a claims officer took on a claim (review, approve, reject, settle). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimReviewHistoryResponse{
    private Long timelineId;
    private Long claimId;
    private String claimNumber;
    private String policyNumber;
    private Long customerId;
    private String claimSource;
    private String action;
    private String oldStatus;
    private String newStatus;
    private String notes;
    private LocalDateTime actedAt;
    private BigDecimal claimedAmount;
    private BigDecimal approvedAmount;
    private String currentStatus;
}
