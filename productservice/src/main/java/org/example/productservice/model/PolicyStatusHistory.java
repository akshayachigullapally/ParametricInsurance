package org.example.productservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("policy_status_history")
public class PolicyStatusHistory{
    @Id
    private Long id;

    @Column("policy_id")
    private Long policyId;

    @Column("old_status")
    private String oldStatus;

    @Column("new_status")
    private String newStatus;

    @Column("changed_by")
    private Long changedBy;

    @Column("change_reason")
    private String changeReason;

    @Column("changed_at")
    private LocalDateTime changedAt;
}
