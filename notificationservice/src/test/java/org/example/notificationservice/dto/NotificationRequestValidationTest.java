package org.example.notificationservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Checks the request validation rules declared on the DTOs. */
class NotificationRequestValidationTest{
    private static final Validator VALIDATOR=Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> messages(Object request){
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void templateSendNeedsATemplateCode(){
        assertThat(messages(new SendFromTemplateRequest())).contains("Template code is required");
    }

    @Test
    void retriesAreLimited(){
        SendFromTemplateRequest request=new SendFromTemplateRequest();
        request.setTemplateCode("CLAIM_APPROVED");
        request.setMaxRetries(11);
        assertThat(messages(request)).contains("Max retries cannot exceed 10");
    }
}
