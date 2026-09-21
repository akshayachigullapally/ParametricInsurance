package org.example.authservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sessions")
public class Session{
    @Id
    private Long id;
    @Column("user_id")
    private Long userId;
    @Column("refresh_token_id")
    private Long refreshTokenId;
    @Column("device_name")
    private String deviceName;
    @Column("ip_address")
    private String ipAddress;
    @Column("active")
    private Boolean active;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("last_accessed_at")
    private LocalDateTime lastAccessedAt;
}