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
@Table("product_threshold_history")
public class ProductThresholdHistory{
    @Id
    private Long id;

    @Column("product_id")
    private Long productId;

    @Column("product_code")
    private String productCode;

    @Column("previous_peril_code")
    private String previousPerilCode;

    @Column("new_peril_code")
    private String newPerilCode;

    @Column("previous_measurement_type")
    private String previousMeasurementType;

    @Column("new_measurement_type")
    private String newMeasurementType;

    @Column("previous_measurement_unit")
    private String previousMeasurementUnit;

    @Column("new_measurement_unit")
    private String newMeasurementUnit;

    @Column("previous_threshold_value")
    private BigDecimal previousThresholdValue;

    @Column("new_threshold_value")
    private BigDecimal newThresholdValue;

    @Column("previous_comparison_operator")
    private String previousComparisonOperator;

    @Column("new_comparison_operator")
    private String newComparisonOperator;

    @Column("previous_aggregation_type")
    private String previousAggregationType;

    @Column("new_aggregation_type")
    private String newAggregationType;

    @Column("previous_observation_window_hours")
    private Integer previousObservationWindowHours;

    @Column("new_observation_window_hours")
    private Integer newObservationWindowHours;

    @Column("previous_minimum_observation_count")
    private Integer previousMinimumObservationCount;

    @Column("new_minimum_observation_count")
    private Integer newMinimumObservationCount;

    @Column("changed_by")
    private Long changedBy;

    @Column("change_reason")
    private String changeReason;

    @Column("changed_at")
    private LocalDateTime changedAt;

    @Column("trigger_sync_status")
    private String triggerSyncStatus;
}