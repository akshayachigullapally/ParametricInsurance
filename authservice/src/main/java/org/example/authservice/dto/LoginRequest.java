package org.example.authservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest{
    @NotBlank(message="Email is required")
    @Email(message="Invalid email")
    private String email;

    @NotBlank(message="Password is required")
    private String password;
}
