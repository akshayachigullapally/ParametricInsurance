package org.example.authservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Checks the request validation rules declared on the DTOs. */
class AuthRequestValidationTest{
    private static final Validator VALIDATOR=Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> messages(Object request){
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void userStatusMustBeKnown(){
        assertThat(messages(UpdateUserStatusRequest.builder().status("DELETED").build()))
                .contains("Status must be ACTIVE, INACTIVE or SUSPENDED");
        assertThat(messages(UpdateUserStatusRequest.builder().status(" suspended ").build())).isEmpty();
    }

    @Test
    void loginInputsAreLimited(){
        LoginRequest request=new LoginRequest();
        request.setEmail("a".repeat(150)+"@x.com");
        request.setPassword("p".repeat(101));
        assertThat(messages(request)).contains(
                "Email must not exceed 150 characters","Password must not exceed 100 characters");
    }
}
