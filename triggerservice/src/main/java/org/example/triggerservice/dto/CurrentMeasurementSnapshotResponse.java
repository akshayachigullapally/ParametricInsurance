package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentMeasurementSnapshotResponse{
    private Long productId;
    private Long locationZoneId;
    private String measurementType;
    private String measurementUnit;
    private BigDecimal currentValue;
    private LocalDateTime recordedAt;
    private LocalDateTime updatedAt;
}
