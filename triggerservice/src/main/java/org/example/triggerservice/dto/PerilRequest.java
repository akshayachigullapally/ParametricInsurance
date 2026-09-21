package org.example.triggerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerilRequest{
    @NotBlank(message="Peril code is required")
    @Size(max=50,message="Peril code cannot exceed 50 characters")
    private String perilCode;

    @NotBlank(message="Peril name is required")
    @Size(max=100,message="Peril name cannot exceed 100 characters")
    private String perilName;

    @NotBlank(message="Peril description is required")
    @Size(max=500,message="Peril description cannot exceed 500 characters")
    private String description;
}