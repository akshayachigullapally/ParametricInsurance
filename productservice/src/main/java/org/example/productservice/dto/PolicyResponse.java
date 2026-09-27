package org.example.productservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyResponse{
    private Long id;
    private String policyNumber;
    private Long customerId;
    private Long productId;
    private String productCode;
    private String productName;
    private BigDecimal sumInsured;
    private BigDecimal premiumAmount;
    private Integer termMonths;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String rejectionReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long locationZoneId;
    private Long triggerSnapshotId;
    private Boolean autoRenew;
    private Long renewedFromPolicyId;
}
