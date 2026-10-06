package org.example.authservice.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequest{
    @NotBlank(message="User status is required")
    @Pattern(regexp="(?i)^\\s*(ACTIVE|INACTIVE|SUSPENDED)\\s*$",message="Status must be ACTIVE, INACTIVE or SUSPENDED")
    private String status;
}