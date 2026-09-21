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
@Table("reconciliation_records")
public class ReconciliationRecord{
    @Id
    private Long id;

    @Column("reconciliation_reference")
    private String reconciliationReference;

    @Column("transaction_type")
    private String transactionType;

    @Column("entity_id")
    private Long entityId;

    @Column("entity_reference")
    private String entityReference;

    @Column("amount")
    private BigDecimal amount;

    @Column("status")
    private String status;

    @Column("notes")
    private String notes;

    @Column("reconciled_by")
    private Long reconciledBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("reconciled_at")
    private LocalDateTime reconciledAt;
}
