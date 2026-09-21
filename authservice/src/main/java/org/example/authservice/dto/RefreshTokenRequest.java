package org.example.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest{
    @NotBlank(message="Refresh token is required")
    private String refreshToken;
}