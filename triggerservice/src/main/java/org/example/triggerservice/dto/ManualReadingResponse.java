package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReadingResponse{
    private MeasurementType measurementType;
    private MeasurementUnit measurementUnit;
    private BigDecimal value;
    private BigDecimal previousValue;
    private LocalDateTime observedAt;
    /** Number of active zones that received the reading. */
    private int zoneCount;
    /** Minutes the demo feed leaves this reading alone before generating values again. */
    private int holdMinutes;
}
