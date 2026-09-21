package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.triggerservice.model.MonitorRunStatus;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorRunResponse{
    private Long id;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private MonitorRunStatus status;
    private Integer observationsProcessed;
    private Integer eventsCreated;
    private String failureMessage;
}
