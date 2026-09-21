package org.example.claimsservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.math.BigDecimal;
import java.time.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("claims")
public class Claim{
    @Id
    private Long id;

    @Column("claim_number")
    private String claimNumber;

    @Column("customer_id")
    private Long customerId;

    @Column("policy_id")
    private Long policyId;

    @Column("policy_number")
    private String policyNumber;

    @Column("product_id")
    private Long productId;

    @Column("incident_type")
    private String incidentType;

    @Column("incident_date")
    private LocalDate incidentDate;

    @Column("incident_location")
    private String incidentLocation;

    private String description;

    @Column("claimed_amount")
    private BigDecimal claimedAmount;

    @Column("approved_amount")
    private BigDecimal approvedAmount;

    private String status;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("reviewed_by")
    private Long reviewedBy;

    @Column("reviewed_at")
    private LocalDateTime reviewedAt;

    @Column("submitted_at")
    private LocalDateTime submittedAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("trigger_event_id")
    private Long triggerEventId;

    @Column("trigger_event_reference")
    private String triggerEventReference;

    @Column("trigger_definition_id")
    private Long triggerDefinitionId;

    @Column("peril_id")
    private Long perilId;

    @Column("location_zone_id")
    private Long locationZoneId;

    @Column("claim_source")
    private String claimSource;

    @Column("manual_review_required")
    private Boolean manualReviewRequired;

    @Column("trigger_detection_status")
    private String triggerDetectionStatus;

    @Column("manual_review_reason")
    private String manualReviewReason;

    @Column("evidence_description")
    private String evidenceDescription;

    @Column("customer_confirmed_loss")
    private Boolean customerConfirmedLoss;

    @Column("created_from_trigger_event")
    private Boolean createdFromTriggerEvent;

    @Column("investigation_result")
    private String investigationResult;

    @Column("investigated_by")
    private Long investigatedBy;

    @Column("investigated_at")
    private LocalDateTime investigatedAt;

    @Column("payout_requested")
    private Boolean payoutRequested;
}
