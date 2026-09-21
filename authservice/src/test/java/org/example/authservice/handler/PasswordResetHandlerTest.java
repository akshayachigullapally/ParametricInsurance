package org.example.authservice.handler;

import jakarta.validation.Validator;
import org.example.authservice.dto.*;
import org.example.authservice.exception.AuthenticationException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.exception.ValidationException;
import org.example.authservice.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetHandlerTest {
    @Mock private PasswordResetService passwordResetService;

    private Validator validator;
    private PasswordResetHandler passwordResetHandler;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean factory=new LocalValidatorFactoryBean();
        factory.afterPropertiesSet();
        validator=factory;
        passwordResetHandler=new PasswordResetHandler(passwordResetService,validator);
    }

    // ======================== forgotPassword ========================

    @Test
    void forgotPassword_whenValidEmail_shouldReturn200() {
        MessageResponse response=MessageResponse.builder()
                .message("Password reset token generated")
                .token("reset-token-123").build();
        when(passwordResetService.generateToken("test@test.com"))
                .thenReturn(Mono.just(response));

        ServerRequest request=requestWithBody(
                ForgotPasswordRequest.builder().email("test@test.com").build());

        StepVerifier.create(passwordResetHandler.forgotPassword(request))
                .assertNext(ex->assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(passwordResetService).generateToken("test@test.com");
    }

    @Test
    void forgotPassword_whenBodyIsEmpty_shouldReturnValidationException() {
        ServerRequest request=emptyBodyRequest();

        StepVerifier.create(passwordResetHandler.forgotPassword(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void forgotPassword_whenEmailIsBlank_shouldReturnValidationException() {
        ServerRequest request=requestWithBody(
                ForgotPasswordRequest.builder().email("  ").build());

        StepVerifier.create(passwordResetHandler.forgotPassword(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void forgotPassword_whenEmailIsInvalid_shouldReturnValidationException() {
        ServerRequest request=requestWithBody(
                ForgotPasswordRequest.builder().email("not-an-email").build());

        StepVerifier.create(passwordResetHandler.forgotPassword(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void forgotPassword_whenUserNotFound_shouldPropagateNotFoundException() {
        when(passwordResetService.generateToken("missing@test.com"))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        ServerRequest request=requestWithBody(
                ForgotPasswordRequest.builder().email("missing@test.com").build());

        StepVerifier.create(passwordResetHandler.forgotPassword(request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                }).verify();
    }

    // ======================== resetPassword ========================

    @Test
    void resetPassword_whenValidRequest_shouldReturn200() {
        MessageResponse response=MessageResponse.builder()
                .message("Password reset successfully").build();
        when(passwordResetService.resetPassword("valid-token","NewPassword@123"))
                .thenReturn(Mono.just(response));

        ServerRequest request=requestWithBody(
                ResetPasswordRequest.builder()
                        .token("valid-token").newPassword("NewPassword@123").build());

        StepVerifier.create(passwordResetHandler.resetPassword(request))
                .assertNext(ex->assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(passwordResetService).resetPassword("valid-token","NewPassword@123");
    }

    @Test
    void resetPassword_whenBodyIsEmpty_shouldReturnValidationException() {
        ServerRequest request=emptyBodyRequest();

        StepVerifier.create(passwordResetHandler.resetPassword(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void resetPassword_whenTokenIsBlank_shouldReturnValidationException() {
        ServerRequest request=requestWithBody(
                ResetPasswordRequest.builder()
                        .token("  ").newPassword("NewPassword@123").build());

        StepVerifier.create(passwordResetHandler.resetPassword(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void resetPassword_whenPasswordIsBlank_shouldReturnValidationException() {
        ServerRequest request=requestWithBody(
                ResetPasswordRequest.builder()
                        .token("valid-token").newPassword("  ").build());

        StepVerifier.create(passwordResetHandler.resetPassword(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void resetPassword_whenPasswordTooShort_shouldReturnValidationException() {
        ServerRequest request=requestWithBody(
                ResetPasswordRequest.builder()
                        .token("valid-token").newPassword("short").build());

        StepVerifier.create(passwordResetHandler.resetPassword(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void resetPassword_whenTokenIsInvalid_shouldPropagateAuthenticationException() {
        when(passwordResetService.resetPassword("invalid-token","NewPassword@123"))
                .thenReturn(Mono.error(new AuthenticationException("Invalid reset token")));

        ServerRequest request=requestWithBody(
                ResetPasswordRequest.builder()
                        .token("invalid-token").newPassword("NewPassword@123").build());

        StepVerifier.create(passwordResetHandler.resetPassword(request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage()).isEqualTo("Invalid reset token");
                }).verify();
    }

    // ======================== mock builders ========================

    @SuppressWarnings("unchecked")
    private ServerRequest requestWithBody(Object body) {
        ServerRequest request=mock(ServerRequest.class);
        when(request.bodyToMono(any(Class.class))).thenAnswer(invocation->{
            Class<?> type=invocation.getArgument(0);
            if(type.isInstance(body)) return Mono.just(body);
            return Mono.empty();
        });

        ServerRequest.Headers headers=mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        return request;
    }

    private ServerRequest emptyBodyRequest() {
        ServerRequest request=mock(ServerRequest.class);
        when(request.bodyToMono(any(Class.class))).thenReturn(Mono.empty());

        ServerRequest.Headers headers=mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        return request;
    }
}
