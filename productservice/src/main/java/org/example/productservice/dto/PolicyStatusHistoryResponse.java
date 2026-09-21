package org.example.productservice.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyStatusHistoryResponse{
    private Long id;
    private Long policyId;
    private String oldStatus;
    private String newStatus;
    private Long changedBy;
    private String changeReason;
    private LocalDateTime changedAt;
}
