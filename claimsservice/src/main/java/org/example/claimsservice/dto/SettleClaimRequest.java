package org.example.claimsservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettleClaimRequest{
    @Size(max=500,message="Notes must not exceed 500 characters")
    private String notes;
}
