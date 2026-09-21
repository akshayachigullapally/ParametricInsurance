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
public class TriggerEventDecisionRequest{
    @NotBlank(message="Review notes are required")
    @Size(max=500,message="Review notes cannot exceed 500 characters")
    private String reviewNotes;
}