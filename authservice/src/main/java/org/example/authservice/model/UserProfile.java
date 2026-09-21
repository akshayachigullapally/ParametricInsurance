package org.example.authservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.time.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_profiles")
public class UserProfile{
    @Id
    private Long id;

    @Column("auth_user_id")
    private Long authUserId;

    @Column("phone_number")
    private String phoneNumber;

    @Column("date_of_birth")
    private LocalDate dateOfBirth;

    @Column("customer_segment")
    private String customerSegment;

    @Column("status")
    private String status;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}