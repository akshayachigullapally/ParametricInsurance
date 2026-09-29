package org.example.triggerservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("location_zones")
public class LocationZone{
    @Id
    @Column("id")
    private Long id;

    @Column("zone_code")
    private String zoneCode;

    @Column("zone_name")
    private String zoneName;

    @Column("country")
    private String country;

    @Column("state")
    private String state;

    @Column("district")
    private String district;

    @Column("postal_code")
    private String postalCode;

    @Column("latitude")
    private BigDecimal latitude;

    @Column("longitude")
    private BigDecimal longitude;

    @Column("radius_km")
    private BigDecimal radiusKm;

    @Column("status")
    private LocationZoneStatus status;

    @Column("created_by")
    private Long createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_by")
    private Long updatedBy;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("weather_alert")
    private Boolean weatherAlert;

    @Column("alert_message")
    private String alertMessage;

    @Column("alert_raised_at")
    private LocalDateTime alertRaisedAt;

    @Column("max_exposure")
    private BigDecimal maxExposure;
}