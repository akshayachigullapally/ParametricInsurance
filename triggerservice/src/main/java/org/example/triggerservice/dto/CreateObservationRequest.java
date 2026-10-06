package org.example.triggerservice.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class CreateObservationRequest{
    @NotNull(message="Location zone ID is required")
    @Positive(message="Location zone ID must be greater than zero")
    private Long locationZoneId;

    @NotBlank(message="Measurement type is required")
    @Size(max=50,message="Measurement type cannot exceed 50 characters")
    private String measurementType;

    @NotBlank(message="Measurement unit is required")
    @Size(max=40,message="Measurement unit cannot exceed 40 characters")
    private String measurementUnit;

    @NotNull(message="Observed value is required")
    @DecimalMin(value="-273.15",message="Observed value is below the supported minimum")
    private BigDecimal observedValue;

    @NotNull(message="Observation time is required")
    @PastOrPresent(message="Observation time cannot be in the future")
    private LocalDateTime observedAt;

    @NotBlank(message="Observation source is required")
    @Pattern(regexp="(?i)^\\s*(MOCK|MANUAL|WEATHER_API|SENSOR)\\s*$",message="Source must be MOCK, MANUAL, WEATHER_API or SENSOR")
    private String source;

    @Size(max=150,message="Source reference cannot exceed 150 characters")
    private String sourceReference;
}