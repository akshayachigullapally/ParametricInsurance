package org.example.productservice.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDecisionRequest{
    @Size(max=500,message="Reason must not exceed 500 characters")
    private String reason;
}