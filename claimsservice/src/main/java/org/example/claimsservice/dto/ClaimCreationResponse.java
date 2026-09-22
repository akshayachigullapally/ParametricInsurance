package org.example.claimsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimCreationResponse {
    private Integer claimsCreated;
    private Integer claimsFailed;
    private List<ClaimResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimResult {
        private Long policyId;
        private String policyNumber;
        private Long claimId;
        private String claimNumber;
        private String status;
        private String errorMessage;
    }
}