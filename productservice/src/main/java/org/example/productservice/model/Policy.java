package org.example.productservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.math.BigDecimal;
import java.time.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("policies")
public class Policy{
    @Id
    private Long id;

    @Column("policy_number")
    private String policyNumber;

    @Column("customer_id")
    private Long customerId;

    @Column("product_id")
    private Long productId;

    @Column("product_code")
    private String productCode;

    @Column("product_name")
    private String productName;

    @Column("sum_insured")
    private BigDecimal sumInsured;

    @Column("premium_amount")
    private BigDecimal premiumAmount;

    @Column("term_months")
    private Integer termMonths;

    @Column("start_date")
    private LocalDate startDate;

    @Column("end_date")
    private LocalDate endDate;

    private String status;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("reviewed_by")
    private Long reviewedBy;

    @Column("reviewed_at")
    private LocalDateTime reviewedAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("location_zone_id")
    private Long locationZoneId;

    @Column("trigger_snapshot_id")
    private Long triggerSnapshotId;

    @Column("auto_renew")
    private Boolean autoRenew;

    @Column("renewed_from_policy_id")
    private Long renewedFromPolicyId;
}
