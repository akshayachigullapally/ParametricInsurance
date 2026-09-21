package org.example.triggerservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationZoneRequest{
    @NotBlank(message="Zone code is required")
    @Size(max=50,message="Zone code cannot exceed 50 characters")
    private String zoneCode;

    @NotBlank(message="Zone name is required")
    @Size(max=100,message="Zone name cannot exceed 100 characters")
    private String zoneName;

    @NotBlank(message="Country is required")
    @Size(max=100,message="Country cannot exceed 100 characters")
    private String country;

    @NotBlank(message="State is required")
    @Size(max=100,message="State cannot exceed 100 characters")
    private String state;

    @NotBlank(message="District is required")
    @Size(max=100,message="District cannot exceed 100 characters")
    private String district;

    @Size(max=20,message="Postal code cannot exceed 20 characters")
    private String postalCode;

    @NotNull(message="Latitude is required")
    @DecimalMin(value="-90.0",message="Latitude must be at least -90")
    @DecimalMax(value="90.0",message="Latitude must not exceed 90")
    private BigDecimal latitude;

    @NotNull(message="Longitude is required")
    @DecimalMin(value="-180.0",message="Longitude must be at least -180")
    @DecimalMax(value="180.0",message="Longitude must not exceed 180")
    private BigDecimal longitude;

    @NotNull(message="Radius is required")
    @Positive(message="Radius must be greater than zero")
    private BigDecimal radiusKm;
}
