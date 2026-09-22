package org.example.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifiedEventProcessingResponse{
    private Long productId;
    private Long eventId;
    private long claimsCreated;
    private String message;
}