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
    private BigDecimal currentValue;
    @NotBlank @Size(max=200)
    private String idempotencyKey;
}
