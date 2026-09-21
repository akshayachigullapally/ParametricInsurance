package org.example.notificationservice.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import org.example.notificationservice.dto.InternalNotificationRequest;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestValidationUtilTest {

    @Mock
    private Validator validator;

    @Mock
    private Path path;

    @InjectMocks
    private RequestValidationUtil validationUtil;

    @Test
    void validate_validRequest_returnsSameObject() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .type(NotificationType.GENERAL)
                .title("Test Title")
                .message("Test Message")
                .build();

        when(validator.validate(request)).thenReturn(Set.of());

        InternalNotificationRequest result = validationUtil.validate(request);

        assertThat(result).isSameAs(request);
    }

    @SuppressWarnings("unchecked")
    @Test
    void validate_invalidRequest_throwsValidationException() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .type(NotificationType.GENERAL)
                .title("")
                .message("Test")
                .build();

        ConstraintViolation<InternalNotificationRequest> violation =
                org.mockito.Mockito.mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(path.toString()).thenReturn("title");
        when(violation.getMessage()).thenReturn("must not be blank");

        when(validator.validate(request)).thenReturn(Set.of(violation));

        assertThatThrownBy(() -> validationUtil.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("title")
                .hasMessageContaining("must not be blank");
    }

    @SuppressWarnings("unchecked")
    @Test
    void validate_multipleViolations_throwsValidationExceptionWithAllMessages() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .type(NotificationType.GENERAL)
                .title("")
                .message("")
                .build();

        ConstraintViolation<InternalNotificationRequest> v1 =
                org.mockito.Mockito.mock(ConstraintViolation.class);
        when(v1.getPropertyPath()).thenReturn(path);
        when(path.toString()).thenReturn("title");
        when(v1.getMessage()).thenReturn("must not be blank");

        ConstraintViolation<InternalNotificationRequest> v2 =
                org.mockito.Mockito.mock(ConstraintViolation.class);
        Path path2 = org.mockito.Mockito.mock(Path.class);
        when(v2.getPropertyPath()).thenReturn(path2);
        when(path2.toString()).thenReturn("message");
        when(v2.getMessage()).thenReturn("must not be blank");

        when(validator.validate(request)).thenReturn(Set.of(v1, v2));

        assertThatThrownBy(() -> validationUtil.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("title")
                .hasMessageContaining("message");
    }
}
