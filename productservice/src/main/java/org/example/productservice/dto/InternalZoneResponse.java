package org.example.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder(toBuilder=true)
@NoArgsConstructor
@AllArgsConstructor
public class InternalZoneResponse {
    private Long zoneId;
    private String zoneCode;
    private String zoneName;
    private String country;
    private String state;
    private String district;
    private String postalCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal radiusKm;
    private String status;
    private Boolean weatherAlert;
    private String alertMessage;
    private java.time.LocalDateTime alertRaisedAt;
    private BigDecimal maxExposure;
}
