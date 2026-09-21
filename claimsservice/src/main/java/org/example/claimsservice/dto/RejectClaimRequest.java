package org.example.claimsservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectClaimRequest{
    @NotBlank(message="Rejection reason is required")
    @Size(max=500,message="Rejection reason must not exceed 500 characters")
    private String reason;
}