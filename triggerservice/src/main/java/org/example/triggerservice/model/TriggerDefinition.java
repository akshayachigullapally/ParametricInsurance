package org.example.triggerservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("trigger_definitions")
public class TriggerDefinition{
    @Id
    private Long id;

    @Column("definition_code")
    private String definitionCode;

    @Column("definition_name")
    private String definitionName;

    private String description;

    @Column("peril_id")
    private Long perilId;

    @Column("location_zone_id")
    private Long locationZoneId;

    @Column("product_id")
    private Long productId;

    @Column("measurement_type")
    private MeasurementType measurementType;

    @Column("measurement_unit")
    private MeasurementUnit measurementUnit;

    @Column("aggregation_type")
    private AggregationType aggregationType;

    @Column("comparison_operator")
    private ComparisonOperator comparisonOperator;

    @Column("threshold_value")
    private BigDecimal thresholdValue;

    @Column("observation_window_hours")
    private Integer observationWindowHours;

    @Column("minimum_observation_count")
    private Integer minimumObservationCount;

    private TriggerDefinitionStatus status;

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