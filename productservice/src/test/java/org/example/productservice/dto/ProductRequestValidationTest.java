package org.example.productservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

/** Checks the request validation rules declared on the DTOs. */
class ProductRequestValidationTest{
    private static final Validator VALIDATOR=Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> messages(Object request){
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private static ProductRequest.ProductRequestBuilder valid(){
        return ProductRequest.builder()
                .productCode("RAIN-1").name("Rain cover").category("WEATHER")
                .minimumSumInsured(new BigDecimal("10000")).maximumSumInsured(new BigDecimal("500000"))
                .minimumTermMonths(1).maximumTermMonths(12);
    }

    @Test
    void validProductHasNoErrors(){
        assertThat(messages(valid().build())).isEmpty();
    }

    @Test
    void maximumsMustNotBeBelowMinimums(){
        assertThat(messages(valid().maximumSumInsured(new BigDecimal("5000")).build()))
                .contains("Maximum sum insured must be greater than or equal to the minimum sum insured");
        assertThat(messages(valid().minimumTermMonths(6).maximumTermMonths(3).build()))
                .contains("Maximum term must be greater than or equal to the minimum term");
    }

    @Test
    void categoryMustBeKnown(){
        assertThat(messages(valid().category("SPACE").build()))
                .anyMatch(message->message.startsWith("Category must be one of"));
        assertThat(messages(valid().category("flood").build())).isEmpty();
    }

    @Test
    void policyTermIsCapped(){
        CreatePolicyRequest request=CreatePolicyRequest.builder()
                .productId(1L).locationZoneId(1L).sumInsured(new BigDecimal("1000.123")).termMonths(121).build();
        assertThat(messages(request)).contains(
                "Policy term cannot exceed 120 months",
                "Sum insured can have at most 2 decimal places");
    }

    @Test
    void verifiedEventAndAutoRenewNeedTheirFields(){
        assertThat(messages(new VerifiedEventRequest())).contains("Product id is required","Event id is required");
        assertThat(messages(new AutoRenewRequest())).contains("enabled is required");
    }
}
