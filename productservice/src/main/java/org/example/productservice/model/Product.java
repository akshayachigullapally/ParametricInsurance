package org.example.productservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("products")
public class Product{
    @Id
    private Long id;

    @Column("product_code")
    private String productCode;

    private String name;
    private String description;
    private String category;
    private String status;

    @Column("minimum_sum_insured")
    private BigDecimal minimumSumInsured;

    @Column("maximum_sum_insured")
    private BigDecimal maximumSumInsured;

    @Column("minimum_term_months")
    private Integer minimumTermMonths;

    @Column("maximum_term_months")
    private Integer maximumTermMonths;

    @Column("eligibility_description")
    private String eligibilityDescription;

    @Column("peril_code")
    private String perilCode;

    @Column("measurement_type")
    private String measurementType;

    @Column("measurement_unit")
    private String measurementUnit;

    @Column("threshold_value")
    private BigDecimal thresholdValue;

    @Column("comparison_operator")
    private String comparisonOperator;

    @Column("aggregation_type")
    private String aggregationType;

    @Column("observation_window_hours")
    private Integer observationWindowHours;

    @Column("minimum_observation_count")
    private Integer minimumObservationCount;

    @Column("trigger_description")
    private String triggerDescription;

    @Column("trigger_sync_status")
    private String triggerSyncStatus;

    @Column("trigger_synced_at")
    private LocalDateTime triggerSyncedAt;

    @Column("trigger_sync_error")
    private String triggerSyncError;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}