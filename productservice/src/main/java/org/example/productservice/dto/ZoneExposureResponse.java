package org.example.productservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** How much cover a zone carries against its cap, and whether sales are paused. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneExposureResponse{
    private Long zoneId;
    private String zoneCode;
    private String zoneName;
    private boolean weatherAlert;
    private String alertMessage;
    private LocalDateTime alertRaisedAt;
    /** Null when the zone has no cap. */
    private BigDecimal maxExposure;
    private BigDecimal activeExposure;
    private BigDecimal pendingExposure;
    /** Active + pending sum insured: pending applications reserve capacity. */
    private BigDecimal usedExposure;
    /** Null when the zone has no cap. */
    private BigDecimal remainingExposure;
    /** usedExposure ÷ maxExposure × 100, one decimal; null when the zone has no cap. */
    private BigDecimal utilizationPercent;
    private long activePolicyCount;
    private long pendingPolicyCount;
    /** True when new policies can currently be sold in the zone. */
    private boolean salesOpen;
}
