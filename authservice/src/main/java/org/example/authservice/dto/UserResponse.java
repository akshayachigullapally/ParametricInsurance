package org.example.authservice.dto;

import lombok.*;
import java.time.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse{
    private Long id;
    private String fullName;
    private String email;
    private String role;
    private Boolean enabled;
    private Boolean emailVerified;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String customerSegment;
    private String profileStatus;
    private List<AddressResponse> addresses;
    private LocalDateTime createdAt;
}