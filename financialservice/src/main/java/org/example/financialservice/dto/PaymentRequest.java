package org.example.financialservice.dto;

import org.example.financialservice.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest{
    @NotNull
    @Positive
    private Long policyId;

    @NotNull
    private PaymentMethod method;
}