package org.example.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest{
    @NotBlank(message="Address line one is required")
    @Size(max=255,message="Address line one must not exceed 255 characters")
    private String addressLineOne;

    @Size(max=255,message="Address line two must not exceed 255 characters")
    private String addressLineTwo;

    @NotBlank(message="City is required")
    @Size(max=100,message="City must not exceed 100 characters")
    private String city;

    @NotBlank(message="State is required")
    @Size(max=100,message="State must not exceed 100 characters")
    private String state;

    @NotBlank(message="Postal code is required")
    @Pattern(regexp="^[0-9]{6}$",message="Postal code must contain 6 digits")
    private String postalCode;

    @NotBlank(message="Country is required")
    @Size(max=100,message="Country must not exceed 100 characters")
    private String country;

    private Boolean primaryAddress;
}
