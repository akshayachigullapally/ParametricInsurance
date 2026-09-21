package org.example.authservice.handler;

import jakarta.validation.Validator;
import org.example.authservice.dto.*;
import org.example.authservice.exception.AuthenticationException;
import org.example.authservice.exception.ConflictException;
import org.example.authservice.exception.ForbiddenException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.exception.ValidationException;
import org.example.authservice.service.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationHandlerTest {
    @Mock private EmailVerificationService verificationService;

    private Validator validator;
    private EmailVerificationHandler emailVerificationHandler;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean factory=new LocalValidatorFactoryBean();
        factory.afterPropertiesSet();
        validator=factory;
        emailVerificationHandler=new EmailVerificationHandler(verificationService,validator);
    }

    // ======================== verify ========================

    @Test
    void verify_whenValidToken_shouldReturn200() {
        MessageResponse response=MessageResponse.builder()
                .message("Email verified successfully").build();
        when(verificationService.verify("valid-token"))
                .thenReturn(Mono.just(response));

        ServerRequest request=requestWithBody(
                VerifyEmailRequest.builder().token("valid-token").build());

        StepVerifier.create(emailVerificationHandler.verify(request))
                .assertNext(ex->assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(verificationService).verify("valid-token");
    }

    @Test
    void verify_whenBodyIsEmpty_shouldReturnValidationException() {
        ServerRequest request=emptyBodyRequest();

        StepVerifier.create(emailVerificationHandler.verify(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void verify_whenTokenIsBlank_shouldReturnValidationException() {
        ServerRequest request=requestWithBody(
                VerifyEmailRequest.builder().token("  ").build());

        StepVerifier.create(emailVerificationHandler.verify(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void verify_whenTokenIsInvalid_shouldPropagateAuthenticationException() {
        when(verificationService.verify("invalid-token"))
                .thenReturn(Mono.error(new AuthenticationException("Invalid verification token")));

        ServerRequest request=requestWithBody(
                VerifyEmailRequest.builder().token("invalid-token").build());

        StepVerifier.create(emailVerificationHandler.verify(request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Invalid verification token");
                }).verify();
    }

    @Test
    void verify_whenEmailAlreadyVerified_shouldPropagateConflictException() {
        when(verificationService.verify("verified-token"))
                .thenReturn(Mono.error(new ConflictException("Email is already verified")));

        ServerRequest request=requestWithBody(
                VerifyEmailRequest.builder().token("verified-token").build());

        StepVerifier.create(emailVerificationHandler.verify(request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Email is already verified");
                }).verify();
    }

    // ======================== resend ========================

    @Test
    void resend_whenAuthenticated_shouldReturn200() {
        MessageResponse response=MessageResponse.builder()
                .message("Verification token generated")
                .token("new-verify-token").build();
        when(verificationService.generateToken(1L))
                .thenReturn(Mono.just(response));

        ServerRequest request=authenticatedRequestNoBody(1L);

        StepVerifier.create(emailVerificationHandler.resend(request))
                .assertNext(ex->assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(verificationService).generateToken(1L);
    }

    @Test
    void resend_whenNotAuthenticated_shouldReturnForbiddenException() {
        ServerRequest request=unauthenticatedRequestNoBody();

        StepVerifier.create(emailVerificationHandler.resend(request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ForbiddenException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Authenticated user is required");
                }).verify();
    }

    @Test
    void resend_whenUserNotFound_shouldPropagateNotFoundException() {
        when(verificationService.generateToken(99L))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        ServerRequest request=authenticatedRequestNoBody(99L);

        StepVerifier.create(emailVerificationHandler.resend(request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                }).verify();
    }

    @Test
    void resend_whenEmailAlreadyVerified_shouldPropagateConflictException() {
        when(verificationService.generateToken(1L))
                .thenReturn(Mono.error(new ConflictException("Email is already verified")));

        ServerRequest request=authenticatedRequestNoBody(1L);

        StepVerifier.create(emailVerificationHandler.resend(request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Email is already verified");
                }).verify();
    }

    // ======================== resendByEmail ========================

    @Test
    void resendByEmail_whenValidEmail_shouldReturn200() {
        MessageResponse response=MessageResponse.builder()
                .message("Verification token generated")
                .token("new-verify-token").build();
        when(verificationService.resendVerification("test@test.com"))
                .thenReturn(Mono.just(response));

        ServerRequest request=requestWithBody(
                ResendVerificationRequest.builder().email("test@test.com").build());

        StepVerifier.create(emailVerificationHandler.resendByEmail(request))
                .assertNext(ex->assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(verificationService).resendVerification("test@test.com");
    }

    @Test
    void resendByEmail_whenBodyIsEmpty_shouldReturnValidationException() {
        ServerRequest request=emptyBodyRequest();

        StepVerifier.create(emailVerificationHandler.resendByEmail(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void resendByEmail_whenEmailIsBlank_shouldReturnValidationException() {
        ServerRequest request=requestWithBody(
                ResendVerificationRequest.builder().email("  ").build());

        StepVerifier.create(emailVerificationHandler.resendByEmail(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void resendByEmail_whenEmailIsInvalid_shouldReturnValidationException() {
        ServerRequest request=requestWithBody(
                ResendVerificationRequest.builder().email("not-an-email").build());

        StepVerifier.create(emailVerificationHandler.resendByEmail(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void resendByEmail_whenUserNotFound_shouldPropagateNotFoundException() {
        when(verificationService.resendVerification("missing@test.com"))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        ServerRequest request=requestWithBody(
                ResendVerificationRequest.builder().email("missing@test.com").build());

        StepVerifier.create(emailVerificationHandler.resendByEmail(request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                }).verify();
    }

    @Test
    void resendByEmail_whenEmailAlreadyVerified_shouldPropagateConflictException() {
        when(verificationService.resendVerification("verified@test.com"))
                .thenReturn(Mono.error(new ConflictException("Email is already verified")));

        ServerRequest request=requestWithBody(
                ResendVerificationRequest.builder().email("verified@test.com").build());

        StepVerifier.create(emailVerificationHandler.resendByEmail(request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Email is already verified");
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

    private ServerRequest authenticatedRequestNoBody(Long userId) {
        ServerRequest request=mock(ServerRequest.class);
        when(request.principal())
                .thenReturn((Mono) Mono.just(new UsernamePasswordAuthenticationToken(userId,null)));

        ServerRequest.Headers headers=mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1",8080)));

        return request;
    }

    private ServerRequest unauthenticatedRequestNoBody() {
        ServerRequest request=mock(ServerRequest.class);
        when(request.principal()).thenReturn(Mono.empty());

        ServerRequest.Headers headers=mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1",8080)));

        return request;
    }
}
