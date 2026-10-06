package org.example.triggerservice.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest{
    @NotBlank(message="Status is required")
    @Pattern(regexp="(?i)^\\s*(ACTIVE|INACTIVE|ARCHIVED)\\s*$",message="Status must be ACTIVE, INACTIVE or ARCHIVED")
    private String status;
}