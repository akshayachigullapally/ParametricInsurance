package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** An admin setting the live reading of one measurement type, e.g. rainfall = 160 mm. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReadingRequest{
    private String measurementType;
    private BigDecimal value;
}
