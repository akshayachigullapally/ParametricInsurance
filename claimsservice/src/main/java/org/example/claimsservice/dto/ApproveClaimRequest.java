package org.example.claimsservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveClaimRequest{
    @NotNull(message="Approved amount is required")
    @Positive(message="Approved amount must be positive")
    private BigDecimal approvedAmount;
}