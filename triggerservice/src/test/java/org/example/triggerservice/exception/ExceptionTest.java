package org.example.triggerservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    @Test
    void dependentServiceException_shouldHaveMessage() {
        DependentServiceException ex = new DependentServiceException("Service unavailable");
        assertThat(ex.getMessage()).isEqualTo("Service unavailable");
    }
}
