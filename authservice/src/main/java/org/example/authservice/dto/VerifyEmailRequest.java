package org.example.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEmailRequest{
    @NotBlank(message="Verification token is required")
    private String token;
}
