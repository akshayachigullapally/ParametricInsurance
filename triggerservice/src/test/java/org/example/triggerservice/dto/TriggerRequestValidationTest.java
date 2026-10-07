package org.example.triggerservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Checks the request validation rules declared on the DTOs. */
class TriggerRequestValidationTest{
    private static final Validator VALIDATOR=Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> messages(Object request){
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void manualReadingNeedsTypeAndValue(){
        assertThat(messages(new ManualReadingRequest())).contains("Measurement type is required","Value is required");
        assertThat(messages(ManualReadingRequest.builder()
                .measurementType("RAINFALL").value(new BigDecimal("160")).productIds(List.of(1L)).build())).isEmpty();
    }
}
