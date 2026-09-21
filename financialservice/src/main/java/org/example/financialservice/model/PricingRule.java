package org.example.financialservice.model;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table("pricing_rules")
public class PricingRule{
    @Id
    private Long id;

    @Column("product_id")
    private Long productId;

    @Column("base_annual_rate")
    private BigDecimal baseAnnualRate;

    @Column("minimum_premium")
    private BigDecimal minimumPremium;

    @Column("term_factor")
    private BigDecimal termFactor;

    @Column("location_zone_id")
    private Long locationZoneId;

    @Column("location_factor")
    private BigDecimal locationFactor;

    private PricingRuleStatus status;

    @Column("effective_from")
    private LocalDate effectiveFrom;

    @Column("effective_to")
    private LocalDate effectiveTo;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_by")
    private Long updatedBy;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}