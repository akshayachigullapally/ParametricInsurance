package org.example.triggerservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("monitor_runs")
public class MonitorRun{
    @Id
    private Long id;

    @Column("started_at")
    private LocalDateTime startedAt;

    @Column("completed_at")
    private LocalDateTime completedAt;

    private MonitorRunStatus status;

    @Column("observations_processed")
    private Integer observationsProcessed;

    @Column("events_created")
    private Integer eventsCreated;

    @Column("failure_message")
    private String failureMessage;
}
