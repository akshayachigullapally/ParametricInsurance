package org.example.authservice.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest{
    @NotBlank(message="Refresh token is required")
    @Size(max=500,message="Refresh token is too long")
    private String refreshToken;
}