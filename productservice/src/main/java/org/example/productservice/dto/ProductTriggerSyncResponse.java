package org.example.productservice.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductTriggerSyncResponse{
    private Long productId;
    private String productCode;
    private String syncStatus;
    private LocalDateTime syncedAt;
    private String message;
}