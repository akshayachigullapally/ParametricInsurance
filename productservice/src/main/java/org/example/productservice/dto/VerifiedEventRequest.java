package org.example.productservice.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifiedEventRequest{
    @NotNull(message="Product id is required")
    @Positive(message="Product id must be positive")
    private Long productId;
    @NotNull(message="Event id is required")
    @Positive(message="Event id must be positive")
    private Long eventId;
}