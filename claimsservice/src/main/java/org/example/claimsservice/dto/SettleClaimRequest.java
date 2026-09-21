package org.example.claimsservice.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettleClaimRequest{
    private String notes;
}
