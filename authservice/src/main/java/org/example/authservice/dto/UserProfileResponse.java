package org.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse{
    private Long id;
    private Long authUserId;
    private String email;
    private String fullName;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String customerSegment;
    private String status;
    private String kycStatus;
    private AddressResponse address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}