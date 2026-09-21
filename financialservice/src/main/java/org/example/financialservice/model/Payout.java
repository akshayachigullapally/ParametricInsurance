package org.example.financialservice.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("payouts")
public class Payout{
    @Id
    private Long id;

    @Column("payout_reference")
    private String payoutReference;

    @Column("customer_id")
    private Long customerId;

    @Column("claim_id")
    private Long claimId;

    @Column("claim_number")
    private String claimNumber;

    @Column("policy_id")
    private Long policyId;

    @Column("trigger_event_id")
    private Long triggerEventId;

    private BigDecimal amount;

    private PayoutStatus status;

    @Column("external_reference")
    private String externalReference;

    @Column("failure_reason")
    private String failureReason;

    @Column("retry_count")
    private Integer retryCount;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("processed_at")
    private LocalDateTime processedAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}