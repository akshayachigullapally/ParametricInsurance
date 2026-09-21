package org.example.authservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("refresh_tokens")
public class RefreshToken{
    @Id
    private Long id;
    @Column("user_id")
    private Long userId;
    @Column("token")
    private String token;
    @Column("expires_at")
    private LocalDateTime expiresAt;
    @Column("revoked")
    private Boolean revoked;
    @Column("created_at")
    private LocalDateTime createdAt;
}