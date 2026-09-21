package org.example.triggerservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("trigger_evaluations")
public class TriggerEvaluation{
    @Id
    private Long id;

    @Column("evaluation_reference")
    private String evaluationReference;

    @Column("trigger_definition_id")
    private Long triggerDefinitionId;

    @Column("location_zone_id")
    private Long locationZoneId;

    @Column("measurement_type")
    private MeasurementType measurementType;

    @Column("window_start")
    private LocalDateTime windowStart;

    @Column("window_end")
    private LocalDateTime windowEnd;

    @Column("observation_count")
    private Integer observationCount;

    @Column("aggregated_value")
    private BigDecimal aggregatedValue;

    @Column("threshold_value")
    private BigDecimal thresholdValue;

    @Column("comparison_operator")
    private ComparisonOperator comparisonOperator;

    private TriggerEvaluationResult result;

    @Column("evaluated_by")
    private Long evaluatedBy;

    @Column("evaluated_at")
    private LocalDateTime evaluatedAt;
}