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
    @Digits(integer=13,fraction=2,message="Approved amount can have at most 2 decimal places")
    private BigDecimal approvedAmount;
}