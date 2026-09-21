package org.example.authservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users_credentials")
public class UserCredential{
    @Id
    private Long id;

    @Column("email")
    private String email;

    @Column("password")
    private String password;

    @Column("full_name")
    private String fullName;

    @Column("role")
    private String role;

    @Column("enabled")
    private Boolean enabled;

    @Column("email_verified")
    private Boolean emailVerified;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}