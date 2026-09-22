package org.example.triggerservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("current_measurements")
public class CurrentMeasurement{
    @Id
    private Long id;
    @Column("product_id")
    private Long productId;
    @Column("location_zone_id")
    private Long locationZoneId;
    @Column("measurement_type")
    private String measurementType;
    @Column("measurement_unit")
    private String measurementUnit;
    @Column("current_value")
    private BigDecimal currentValue;
    @Column("recorded_by")
    private Long recordedBy;
    @Column("recorded_at")
    private LocalDateTime recordedAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;
}
