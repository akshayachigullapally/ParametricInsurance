package org.example.authservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest{
    @NotBlank(message="Full name is required")
    @Size(min=2,max=150,message="Full name must be 2 to 150 characters")
    private String fullName;

    @NotBlank(message="Email is required")
    @Email(message="Invalid email")
    private String email;

    @NotBlank(message="Password is required")
    @Size(min=8,max=100,message="Password must be 8 to 100 characters")
    private String password;
}