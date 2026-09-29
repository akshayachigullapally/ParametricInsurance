package org.example.triggerservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalZoneResponse{
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
    private LocalDateTime alertRaisedAt;
    private BigDecimal maxExposure;
}
