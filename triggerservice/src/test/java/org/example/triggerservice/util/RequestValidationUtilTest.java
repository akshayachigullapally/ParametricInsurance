package org.example.triggerservice.util;

import org.example.triggerservice.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestValidationUtilTest {

    @InjectMocks
    private RequestValidationUtil requestValidationUtil;

    private Validator createMockValidator(Set violations) {
        Validator validator = mock(Validator.class);
        when(validator.validate(any())).thenReturn(violations);
        return validator;
    }

    @Test
    void validate_shouldReturnRequestWhenNoViolations() {
        Validator validator = createMockValidator(Collections.emptySet());
        RequestValidationUtil util = new RequestValidationUtil(validator);

        String request = "valid";

        StepVerifier.create(util.validate(request))
                .assertNext(r -> assertThat(r).isEqualTo("valid"))
                .verifyComplete();
    }

    @Test
    void validate_shouldThrowValidationExceptionWhenViolationsExist() {
        ConstraintViolation<String> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("Field is required");

        Validator validator = createMockValidator(Set.of(violation));
        RequestValidationUtil util = new RequestValidationUtil(validator);

        StepVerifier.create(util.validate(""))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void validate_shouldJoinMultipleViolationMessages() {
        ConstraintViolation<String> v1 = mock(ConstraintViolation.class);
        when(v1.getMessage()).thenReturn("Field A is required");

        ConstraintViolation<String> v2 = mock(ConstraintViolation.class);
        when(v2.getMessage()).thenReturn("Field B is required");

        Validator validator = createMockValidator(Set.of(v1, v2));
        RequestValidationUtil util = new RequestValidationUtil(validator);

        StepVerifier.create(util.validate(""))
                .expectError(ValidationException.class)
                .verify();
    }
}
