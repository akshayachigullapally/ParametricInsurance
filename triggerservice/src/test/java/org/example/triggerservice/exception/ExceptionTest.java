package org.example.triggerservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    @Test
    void notFoundException_shouldHaveMessage() {
        NotFoundException ex = new NotFoundException("Not found");
        assertThat(ex.getMessage()).isEqualTo("Not found");
    }

    @Test
    void conflictException_shouldHaveMessage() {
        ConflictException ex = new ConflictException("Conflict");
        assertThat(ex.getMessage()).isEqualTo("Conflict");
    }

    @Test
    void forbiddenException_shouldHaveMessage() {
        ForbiddenException ex = new ForbiddenException("Forbidden");
        assertThat(ex.getMessage()).isEqualTo("Forbidden");
    }

    @Test
    void dependentServiceException_shouldHaveMessage() {
        DependentServiceException ex = new DependentServiceException("Service unavailable");
        assertThat(ex.getMessage()).isEqualTo("Service unavailable");
    }

    @Test
    void validationException_shouldHaveSingleError() {
        ValidationException ex = new ValidationException("Invalid input");
        assertThat(ex.getErrors()).containsExactly("Invalid input");
        assertThat(ex.getMessage()).isEqualTo("Validation failed");
    }

    @Test
    void validationException_shouldHaveMultipleErrors() {
        ValidationException ex = new ValidationException(java.util.List.of("Error 1", "Error 2"));
        assertThat(ex.getErrors()).containsExactly("Error 1", "Error 2");
        assertThat(ex.getMessage()).isEqualTo("Validation failed");
    }

    @Test
    void errorResponse_shouldBuildCorrectly() {
        ErrorResponse response = ErrorResponse.builder()
                .status(404)
                .error("Not Found")
                .messages(java.util.List.of("Resource not found"))
                .path("/api/test")
                .build();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getError()).isEqualTo("Not Found");
        assertThat(response.getMessages()).containsExactly("Resource not found");
        assertThat(response.getPath()).isEqualTo("/api/test");
        assertThat(response.getTimestamp()).isNull();
    }
}
