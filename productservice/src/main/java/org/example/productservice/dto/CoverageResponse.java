package org.example.productservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageResponse{
    private Long id;
    private String coverageCode;
    private String name;
    private String description;
    private BigDecimal coverageLimit;
    private Boolean mandatory;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}