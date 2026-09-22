package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductTriggerSyncResult{
    private Long productId;
    private String productCode;
    private boolean productActive;
    private int definitionsSynced;
    private int definitionsDeactivated;
    private String message;
}