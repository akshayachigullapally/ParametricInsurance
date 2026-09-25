package org.example.productservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.*;

/** One approve/reject decision made by an underwriter, with the policy it applied to. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyReviewHistoryResponse{
    private Long historyId;
    private Long policyId;
    private String policyNumber;
    private Long customerId;
    private Long productId;
    private String productCode;
    private String productName;
    private BigDecimal sumInsured;
    private BigDecimal premiumAmount;
    private Integer termMonths;
    private String decision;
    private String reason;
    private LocalDateTime decidedAt;
    private String currentStatus;
}
