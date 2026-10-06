package org.example.triggerservice.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** An admin setting the live reading of one measurement type, e.g. rainfall = 160 mm. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReadingRequest{
    @NotBlank(message="Measurement type is required")
    @Size(max=50,message="Measurement type must not exceed 50 characters")
    private String measurementType;
    @NotNull(message="Value is required")
    @DecimalMin(value="-273.15",message="Value is below the supported range")
    @DecimalMax(value="100000",message="Value is above the supported range")
    private BigDecimal value;
    /** Products that use this measurement; each is checked for triggered policies, like the trigger simulator. */
    @Size(max=200,message="Too many products")
    private List<Long> productIds;
}
