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

    @Test
    void manualReadingRangeDependsOnTheMeasurement(){
        assertThat(messages(ManualReadingRequest.builder().measurementType("RAINFALL").value(new BigDecimal("-1")).build()))
                .contains("Value cannot be negative");
        assertThat(messages(ManualReadingRequest.builder().measurementType("temperature").value(new BigDecimal("-4")).build()))
                .isEmpty();
        assertThat(messages(ManualReadingRequest.builder().measurementType("HUMIDITY").value(new BigDecimal("101")).build()))
                .contains("A percentage cannot be more than 100");
        assertThat(messages(ManualReadingRequest.builder().measurementType("SOIL_MOISTURE").value(new BigDecimal("100")).build()))
                .isEmpty();
    }

    @Test
    void zoneStatusMustBeKnown(){
        assertThat(messages(UpdateStatusRequest.builder().status("DELETED").build()))
                .contains("Status must be ACTIVE, INACTIVE or ARCHIVED");
        assertThat(messages(UpdateStatusRequest.builder().status("inactive").build())).isEmpty();
    }

    @Test
    void definitionEndCannotBeBeforeStart(){
        TriggerDefinitionRequest request=TriggerDefinitionRequest.builder()
                .effectiveFrom(LocalDate.of(2026,5,1)).effectiveTo(LocalDate.of(2026,4,1)).build();
        assertThat(messages(request)).contains("Effective end date cannot be before the effective start date");
    }

    @Test
    void demoRequestsNeedIds(){
        assertThat(messages(new DemoSimulationRequest())).contains("Product id is required");
        assertThat(messages(new DemoObservationRequest())).contains(
                "Product id is required","Trigger definition id is required","Location zone id is required");
    }
}
