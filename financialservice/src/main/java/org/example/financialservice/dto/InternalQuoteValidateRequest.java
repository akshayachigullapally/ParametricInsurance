package org.example.financialservice.dto;

import java.math.BigDecimal;
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
public class InternalQuoteValidateRequest{
    @NotNull
    @Positive
    private Long customerId;

    @NotNull
    @Positive
    private Long productId;

    @NotNull
    @Positive
    private Long locationZoneId;

    @NotNull
    @Positive
    private BigDecimal sumInsured;

    @NotNull
    @Positive
    private Integer termMonths;
}