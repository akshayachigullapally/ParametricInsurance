package org.example.financialservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Checks the request validation rules declared on the DTOs. */
class PricingRuleRequestValidationTest{
    private static final Validator VALIDATOR=Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> messages(Object request){
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private static PricingRuleRequest.PricingRuleRequestBuilder valid(){
        return PricingRuleRequest.builder()
                .productId(1L).baseAnnualRate(new BigDecimal("0.035")).minimumPremium(new BigDecimal("250"))
                .termFactor(BigDecimal.ONE).locationFactor(BigDecimal.ONE).effectiveFrom(LocalDate.of(2026,1,1));
    }

    @Test
    void validRuleHasNoErrors(){
        assertThat(messages(valid().build())).isEmpty();
    }

    @Test
    void effectiveToCannotBeBeforeEffectiveFrom(){
        assertThat(messages(valid().effectiveTo(LocalDate.of(2025,12,31)).build()))
                .contains("Effective to date cannot be before the effective from date");
        assertThat(messages(valid().effectiveTo(LocalDate.of(2026,1,1)).build())).isEmpty();
    }

    @Test
    void internalPricingNeedsAllFields(){
        assertThat(messages(new InternalPricingRequest())).contains(
                "Product id is required","Location zone id is required","Sum insured is required","Term is required");
    }

    @Test
    void payoutActionTextIsLimited(){
        assertThat(messages(PayoutActionRequest.builder().failureReason("x".repeat(501)).build()))
                .contains("Failure reason must not exceed 500 characters");
    }
}
