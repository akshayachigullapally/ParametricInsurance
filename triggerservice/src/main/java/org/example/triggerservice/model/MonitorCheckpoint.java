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
@Table("monitor_checkpoints")
public class MonitorCheckpoint{
    @Id
    private Long id;

    @Column("source_code")
    private String sourceCode;

    @Column("location_zone_id")
    private Long locationZoneId;

    @Column("last_processed_timestamp")
    private LocalDateTime lastProcessedTimestamp;

    @Column("last_observation_id")
    private Long lastObservationId;
}
