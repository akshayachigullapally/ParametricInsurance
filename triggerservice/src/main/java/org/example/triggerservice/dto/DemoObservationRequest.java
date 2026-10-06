package org.example.triggerservice.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoObservationRequest {
    @NotNull(message="Product id is required")
    @Positive(message="Product id must be positive")
    private Long productId;
    @NotNull(message="Trigger definition id is required")
    @Positive(message="Trigger definition id must be positive")
    private Long triggerDefinitionId;
    @NotNull(message="Location zone id is required")
    @Positive(message="Location zone id must be positive")
    private Long locationZoneId;
    @NotBlank(message="Measurement type is required")
    private String measurementType;
    @NotBlank(message="Measurement unit is required")
    private String measurementUnit;
    @NotNull(message="Observed value is required")
    private BigDecimal observedValue;
    @NotNull(message="Observation time is required")
    @PastOrPresent(message="Observation time cannot be in the future")
    private LocalDateTime observedAt;
    @Size(max=150,message="Source reference cannot exceed 150 characters")
    private String sourceReference;
    private boolean validateImmediately;
    private boolean evaluateImmediately;
    private boolean autoVerifyMatchedEvent;
    private boolean createAutomaticClaims;
}