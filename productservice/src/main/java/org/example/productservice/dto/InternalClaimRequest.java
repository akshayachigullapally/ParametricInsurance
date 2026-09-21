package org.example.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalClaimRequest{
    private Long policyId;
    private String incidentType;
    private LocalDate incidentDate;
    private String incidentLocation;
    private String description;
    private BigDecimal claimedAmount;
    private Long triggerEventId;
    private String claimSource;
}
