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
@Table("idempotency_records")
public class IdempotencyRecord{
    @Id
    private Long id;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Column("operation_type")
    private String operationType;

    @Column("resource_id")
    private Long resourceId;

    @Column("created_at")
    private LocalDateTime createdAt;
}
