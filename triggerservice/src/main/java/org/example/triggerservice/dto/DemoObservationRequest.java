package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoObservationRequest {
    private Long productId;
    private Long triggerDefinitionId;
    private Long locationZoneId;
    private String measurementType;
    private String measurementUnit;
    private BigDecimal observedValue;
    private LocalDateTime observedAt;
    private String sourceReference;
    private boolean validateImmediately;
    private boolean evaluateImmediately;
    private boolean autoVerifyMatchedEvent;
    private boolean createAutomaticClaims;
}