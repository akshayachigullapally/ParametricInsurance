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
@Table("trigger_events")
public class TriggerEvent{
    @Id
    private Long id;

    @Column("event_reference")
    private String eventReference;

    @Column("trigger_definition_id")
    private Long triggerDefinitionId;

    @Column("trigger_evaluation_id")
    private Long triggerEvaluationId;

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

    @Column("aggregated_value")
    private BigDecimal aggregatedValue;

    @Column("threshold_value")
    private BigDecimal thresholdValue;

    @Column("window_start")
    private LocalDateTime windowStart;

    @Column("window_end")
    private LocalDateTime windowEnd;

    private TriggerEventStatus status;

    @Column("detected_at")
    private LocalDateTime detectedAt;

    @Column("reviewed_by")
    private Long reviewedBy;

    @Column("reviewed_at")
    private LocalDateTime reviewedAt;

    @Column("review_notes")
    private String reviewNotes;
}