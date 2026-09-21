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
@Table("quotes")
public class Quote{
    @Id
    private Long id;

    @Column("quote_reference")
    private String quoteReference;

    @Column("customer_id")
    private Long customerId;

    @Column("product_id")
    private Long productId;

    @Column("location_zone_id")
    private Long locationZoneId;

    @Column("sum_insured")
    private BigDecimal sumInsured;

    @Column("term_months")
    private Integer termMonths;

    @Column("annual_rate")
    private BigDecimal annualRate;

    @Column("term_factor")
    private BigDecimal termFactor;

    @Column("location_factor")
    private BigDecimal locationFactor;

    @Column("premium_amount")
    private BigDecimal premiumAmount;

    private QuoteStatus status;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("accepted_at")
    private LocalDateTime acceptedAt;

    @Column("policy_id")
    private Long policyId;

    @Column("policy_number")
    private String policyNumber;

    @Column("consumed_at")
    private LocalDateTime consumedAt;
}