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
public class ObservationRejectRequest{
    @NotBlank(message="Rejection reason is required")
    @Size(max=500,message="Rejection reason cannot exceed 500 characters")
    private String reason;
}
