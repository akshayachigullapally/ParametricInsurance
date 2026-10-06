package org.example.triggerservice.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoSimulationRequest{
    @NotNull(message="Product id is required")
    @Positive(message="Product id must be positive")
    private Long productId;
}