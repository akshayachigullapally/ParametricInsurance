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
@Table("observations")
public class Observation{
    @Id
    private Long id;

    @Column("observation_reference")
    private String observationReference;

    @Column("location_zone_id")
    private Long locationZoneId;

    @Column("measurement_type")
    private MeasurementType measurementType;

    @Column("measurement_unit")
    private MeasurementUnit measurementUnit;

    @Column("observed_value")
    private BigDecimal observedValue;

    @Column("observed_at")
    private LocalDateTime observedAt;

    private ObservationSource source;

    @Column("source_reference")
    private String sourceReference;

    private ObservationStatus status;

    @Column("received_by")
    private Long receivedBy;

    @Column("received_at")
    private LocalDateTime receivedAt;
}