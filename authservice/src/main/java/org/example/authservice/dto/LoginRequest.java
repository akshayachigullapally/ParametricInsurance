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
    @Size(max=150,message="Email must not exceed 150 characters")
    private String email;

    @NotBlank(message="Password is required")
    @Size(max=100,message="Password must not exceed 100 characters")
    private String password;
}
