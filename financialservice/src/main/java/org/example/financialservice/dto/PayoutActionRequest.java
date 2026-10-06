package org.example.financialservice.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutActionRequest{
    @Size(max=100,message="External reference must not exceed 100 characters")
    private String externalReference;
    @Size(max=500,message="Failure reason must not exceed 500 characters")
    private String failureReason;
}