package org.example.authservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest{
    @NotBlank(message="Full name is required")
    @Size(min=2,max=150,message="Full name must be 2 to 150 characters")
    private String fullName;

    @NotBlank(message="Phone number is required")
    @Pattern(regexp="^[6-9][0-9]{9}$",message="Phone number must contain 10 valid digits")
    private String phoneNumber;

    @NotNull(message="Date of birth is required")
    @Past(message="Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message="Customer segment is required")
    @Pattern(regexp="(?i)^\\s*(INDIVIDUAL|FARMER|SMALL_BUSINESS|ENTERPRISE)\\s*$",message="Customer segment must be INDIVIDUAL, FARMER, SMALL_BUSINESS or ENTERPRISE")
    private String customerSegment;

    @Valid
    @NotNull(message="Address is required")
    private AddressRequest address;
}