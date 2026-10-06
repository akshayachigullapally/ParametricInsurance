package org.example.financialservice.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class CreateQuoteRequest{
    @NotNull
    @Positive
    private Long productId;

    @NotNull
    @Positive
    private Long locationZoneId;

    @NotNull
    @DecimalMin(value="1.00")
    @Digits(integer=13,fraction=2,message="Sum insured can have at most 2 decimal places")
    private BigDecimal sumInsured;

    @NotNull
    @Min(1)
    @Max(60)
    private Integer termMonths;
}