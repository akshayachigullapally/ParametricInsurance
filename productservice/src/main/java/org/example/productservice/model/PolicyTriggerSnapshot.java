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
@Table("policy_trigger_snapshots")
public class PolicyTriggerSnapshot{
    @Id
    private Long id;

    @Column("policy_id")
    private Long policyId;

    @Column("peril_code")
    private String perilCode;

    @Column("trigger_metric")
    private String triggerMetric;

    @Column("trigger_operator")
    private String triggerOperator;

    @Column("trigger_threshold")
    private BigDecimal triggerThreshold;

    @Column("trigger_duration_hours")
    private Integer triggerDurationHours;

    @Column("trigger_aggregation_type")
    private String triggerAggregationType;

    @Column("trigger_min_observation_count")
    private Integer triggerMinObservationCount;

    @Column("product_id")
    private Long productId;

    @Column("product_code")
    private String productCode;

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

    @Column("captured_at")
    private LocalDateTime capturedAt;

    @Column("created_at")
    private LocalDateTime createdAt;
}
