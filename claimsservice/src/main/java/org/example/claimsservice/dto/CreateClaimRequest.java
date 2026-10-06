package org.example.claimsservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClaimRequest{
    @NotNull(message="Policy id is required")
    @Positive(message="Policy id must be positive")
    private Long policyId;

    @NotBlank(message="Incident type is required")
    @Size(max=100,message="Incident type must not exceed 100 characters")
    private String incidentType;

    @NotNull(message="Incident date is required")
    @PastOrPresent(message="Incident date cannot be in the future")
    private LocalDate incidentDate;

    @NotBlank(message="Incident location is required")
    @Size(max=255,message="Incident location must not exceed 255 characters")
    private String incidentLocation;

    @NotBlank(message="Description is required")
    @Size(min=10,max=1000,message="Description must be 10 to 1000 characters")
    private String description;

    @NotNull(message="Claimed amount is required")
    @Positive(message="Claimed amount must be positive")
    @Digits(integer=13,fraction=2,message="Claimed amount can have at most 2 decimal places")
    private BigDecimal claimedAmount;

    @Positive(message="Trigger event id must be positive")
    private Long triggerEventId;

    @Pattern(regexp="(?i)^\\s*(AUTO_TRIGGERED|MANUAL_EXCEPTION)\\s*$",message="Claim source must be AUTO_TRIGGERED or MANUAL_EXCEPTION")
    private String claimSource;

    @Size(max=2000,
            message="Evidence description must not exceed 2000 characters")
    private String evidenceDescription;

    private Boolean customerConfirmedLoss;
}