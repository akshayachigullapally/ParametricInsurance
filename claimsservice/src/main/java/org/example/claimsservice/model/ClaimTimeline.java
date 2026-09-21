package org.example.claimsservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("claim_timeline")
public class ClaimTimeline{
    @Id
    private Long id;

    @Column("claim_id")
    private Long claimId;

    @Column("claim_number")
    private String claimNumber;

    @Column("customer_id")
    private Long customerId;

    @Column("old_status")
    private String oldStatus;

    @Column("new_status")
    private String newStatus;

    @Column("action")
    private String action;

    @Column("action_by")
    private Long actionBy;

    @Column("action_by_role")
    private String actionByRole;

    @Column("notes")
    private String notes;

    @Column("created_at")
    private LocalDateTime createdAt;
}
