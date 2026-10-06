package org.example.claimsservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;

/** Checks the request validation rules declared on the DTOs. */
class ClaimRequestValidationTest{
    private static final Validator VALIDATOR=Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> messages(Object request){
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void triggerClaimsNeedAnEventAndMatchedPolicies(){
        assertThat(messages(new ClaimCreationRequest())).contains(
                "Trigger event id is required","At least one matched policy is required");
    }

    @Test
    void eachMatchedPolicyIsChecked(){
        ClaimCreationRequest request=ClaimCreationRequest.builder()
                .triggerEventId(9L)
                .eligiblePolicies(List.of(ClaimCreationRequest.EligiblePolicyInfo.builder().build()))
                .build();
        assertThat(messages(request)).contains("Policy id is required","Customer id is required","Sum insured is required");
    }

    @Test
    void claimSourceMustBeKnown(){
        CreateClaimRequest request=CreateClaimRequest.builder().claimSource("GUESS").claimedAmount(new BigDecimal("10.555")).build();
        assertThat(messages(request)).contains(
                "Claim source must be AUTO_TRIGGERED or MANUAL_EXCEPTION",
                "Claimed amount can have at most 2 decimal places");
    }

    @Test
    void settleNotesAreLimited(){
        SettleClaimRequest request=SettleClaimRequest.builder().notes("x".repeat(501)).build();
        assertThat(messages(request)).contains("Notes must not exceed 500 characters");
    }
}
