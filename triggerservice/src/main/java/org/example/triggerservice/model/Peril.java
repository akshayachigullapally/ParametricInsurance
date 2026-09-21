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
@Table("perils")
public class Peril{
    @Id
    private Long id;

    @Column("peril_code")
    private String perilCode;

    @Column("peril_name")
    private String perilName;

    private String description;
    private PerilStatus status;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_by")
    private Long updatedBy;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}