package org.example.claimsservice.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimTimelineResponse{
    private Long id;
    private String oldStatus;
    private String newStatus;
    private String action;
    private Long actionBy;
    private String actionByRole;
    private String notes;
    private LocalDateTime createdAt;
}
