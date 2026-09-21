package org.example.productservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageRequest{
    @NotBlank(message="Coverage code is required")
    @Size(max=50,message="Coverage code must not exceed 50 characters")
    private String coverageCode;

    @NotBlank(message="Coverage name is required")
    @Size(max=150,message="Coverage name must not exceed 150 characters")
    private String name;

    @Size(max=1000,message="Coverage description must not exceed 1000 characters")
    private String description;

    @PositiveOrZero(message="Coverage limit cannot be negative")
    private BigDecimal coverageLimit;

    private Boolean mandatory;
    private Boolean active;
}