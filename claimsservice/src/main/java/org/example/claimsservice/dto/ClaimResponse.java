package org.example.claimsservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimResponse{
    private Long id;
    private String claimNumber;
    private Long customerId;
    private Long policyId;
    private String policyNumber;
    private Long productId;
    private String incidentType;
    private LocalDate incidentDate;
    private String incidentLocation;
    private String description;
    private BigDecimal claimedAmount;
    private BigDecimal approvedAmount;
    private String status;
    private String rejectionReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private Long triggerEventId;
    private String triggerEventReference;
    private Long triggerDefinitionId;
    private Long perilId;
    private Long locationZoneId;
    private String claimSource;
    private Boolean manualReviewRequired;
    private String triggerDetectionStatus;
    private String manualReviewReason;
    private String evidenceDescription;
    private Boolean customerConfirmedLoss;
    private Boolean createdFromTriggerEvent;
    private String investigationResult;
    private Long investigatedBy;
    private LocalDateTime investigatedAt;
    private Boolean payoutRequested;
    private String triggerMeasurementType;
    private String triggerMeasurementUnit;
    private BigDecimal triggerObservedValue;
    private BigDecimal triggerThresholdValue;
    private String triggerComparisonOperator;
    private LocalDateTime triggerWindowStart;
    private LocalDateTime triggerWindowEnd;
}
