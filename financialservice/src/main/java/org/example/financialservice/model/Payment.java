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
@Table("payments")
public class Payment{
    @Id
    private Long id;

    @Column("payment_reference")
    private String paymentReference;

    @Column("customer_id")
    private Long customerId;

    @Column("policy_id")
    private Long policyId;

    @Column("policy_number")
    private String policyNumber;

    @Column("quote_id")
    private Long quoteId;

    private BigDecimal amount;

    private PaymentMethod method;

    private PaymentStatus status;

    @Column("external_reference")
    private String externalReference;

    @Column("failure_reason")
    private String failureReason;

    @Column("attempt_number")
    private Integer attemptNumber;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("completed_at")
    private LocalDateTime completedAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}