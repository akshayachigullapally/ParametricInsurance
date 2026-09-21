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
@Table("financial_transactions")
public class FinancialTransaction{
    @Id
    private Long id;

    @Column("transaction_reference")
    private String transactionReference;

    @Column("customer_id")
    private Long customerId;

    @Column("policy_id")
    private Long policyId;

    @Column("quote_id")
    private Long quoteId;

    @Column("payment_id")
    private Long paymentId;

    @Column("payout_id")
    private Long payoutId;

    @Column("transaction_type")
    private String transactionType;

    @Column("amount")
    private BigDecimal amount;

    @Column("status")
    private String status;

    @Column("description")
    private String description;

    @Column("created_at")
    private LocalDateTime createdAt;
}
