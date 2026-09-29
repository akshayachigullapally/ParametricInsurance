package org.example.triggerservice.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneExposureCapRequest{
    /** Maximum total sum insured of open policies in the zone; null removes the cap. */
    @DecimalMin(value="1",message="Exposure cap must be greater than zero")
    private BigDecimal maxExposure;
}
