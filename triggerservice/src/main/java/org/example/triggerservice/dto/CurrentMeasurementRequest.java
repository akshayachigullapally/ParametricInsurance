package org.example.triggerservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentMeasurementRequest{
    @NotNull @Positive
    private Long productId;
    @NotNull @Positive
    private Long locationZoneId;
    @NotNull @DecimalMin("0.0")
    @DecimalMax(value="100000",message="Current value is above the supported range")
    private BigDecimal currentValue;
    @NotBlank @Size(max=200)
    private String idempotencyKey;
}
