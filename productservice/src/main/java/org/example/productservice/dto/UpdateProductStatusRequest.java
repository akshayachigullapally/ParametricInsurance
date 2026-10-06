package org.example.productservice.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductStatusRequest{
    @NotBlank(message="Product status is required")
    @Pattern(regexp="(?i)^\\s*(DRAFT|ACTIVE|INACTIVE|ARCHIVED)\\s*$",message="Status must be DRAFT, ACTIVE, INACTIVE or ARCHIVED")
    private String status;
}