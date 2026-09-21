package org.example.authservice.service;

import org.example.authservice.exception.AuthenticationException;
import org.example.authservice.exception.ConflictException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.model.EmailVerificationToken;
import org.example.authservice.model.UserCredential;
import org.example.authservice.repository.EmailVerificationTokenRepository;
import org.example.authservice.repository.UserCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private UserCredentialRepository userRepository;

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        emailVerificationService=new EmailVerificationService(
                tokenRepository,userRepository
        );
        ReflectionTestUtils.setField(emailVerificationService,"tokenExpirationHours",24L);
    }

    @Test
    void generateToken_whenUserExistsAndNotVerified_shouldCreateToken() {
        UserCredential user=user(1L,false);

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(tokenRepository.findByUserIdAndVerifiedFalse(1L))
                .thenReturn(Mono.empty());
        when(tokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation->{
                    EmailVerificationToken token=invocation.getArgument(0);
                    token.setId(100L);
                    return Mono.just(token);
                });

        StepVerifier.create(emailVerificationService.generateToken(1L))
                .assertNext(response->{
                    assertThat(response.getMessage())
                            .isEqualTo("Verification token generated");
                    assertThat(response.getToken()).isNotBlank();
                }).verifyComplete();

        ArgumentCaptor<EmailVerificationToken> captor=
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getVerified()).isFalse();
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void generateToken_whenUserExistsAndHasUnverifiedToken_shouldUpdateToken() {
        UserCredential user=user(1L,false);
        EmailVerificationToken existingToken=existingToken(1L,"old-token",false);

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(tokenRepository.findByUserIdAndVerifiedFalse(1L))
                .thenReturn(Mono.just(existingToken));
        when(tokenRepository.save(existingToken)).thenReturn(Mono.just(existingToken));

        StepVerifier.create(emailVerificationService.generateToken(1L))
                .assertNext(response->{
                    assertThat(response.getMessage())
                            .isEqualTo("Verification token generated");
                    assertThat(response.getToken()).isNotBlank();
                    assertThat(response.getToken())
                            .isNotEqualTo("old-token");
                }).verifyComplete();

        verify(tokenRepository).save(existingToken);
    }

    @Test
    void generateToken_whenUserDoesNotExist_shouldReturnNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(emailVerificationService.generateToken(99L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                }).verify();

        verify(tokenRepository,never()).save(any());
    }

    @Test
    void generateToken_whenEmailIsAlreadyVerified_shouldReturnConflictException() {
        UserCredential user=user(1L,true);

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));

        StepVerifier.create(emailVerificationService.generateToken(1L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Email is already verified");
                }).verify();

        verify(tokenRepository,never()).findByUserIdAndVerifiedFalse(anyLong());
        verify(tokenRepository,never()).save(any());
    }

    @Test
    void verify_whenTokenIsValid_shouldMarkVerifiedAndUpdateUser() {
        EmailVerificationToken token=existingToken(1L,"valid-token",false);
        UserCredential user=user(1L,false);

        when(tokenRepository.findByToken("valid-token"))
                .thenReturn(Mono.just(token));
        when(tokenRepository.save(token)).thenReturn(Mono.just(token));
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(userRepository.save(user)).thenReturn(Mono.just(user));

        StepVerifier.create(emailVerificationService.verify("valid-token"))
                .assertNext(response->assertThat(response.getMessage())
                        .isEqualTo("Email verified successfully"))
                .verifyComplete();

        assertThat(token.getVerified()).isTrue();
        assertThat(user.getEmailVerified()).isTrue();
    }

    @Test
    void verify_whenTokenDoesNotExist_shouldReturnAuthenticationException() {
        when(tokenRepository.findByToken("missing-token"))
                .thenReturn(Mono.empty());

        StepVerifier.create(emailVerificationService.verify("missing-token"))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Invalid verification token");
                }).verify();

        verify(userRepository,never()).findById(anyLong());
    }

    @Test
    void verify_whenTokenIsAlreadyVerified_shouldReturnConflictException() {
        EmailVerificationToken token=existingToken(1L,"verified-token",true);

        when(tokenRepository.findByToken("verified-token"))
                .thenReturn(Mono.just(token));

        StepVerifier.create(emailVerificationService.verify("verified-token"))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Email is already verified");
                }).verify();

        verify(userRepository,never()).findById(anyLong());
    }

    @Test
    void verify_whenTokenIsExpired_shouldReturnAuthenticationException() {
        EmailVerificationToken token=existingToken(1L,"expired-token",false);
        token.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(tokenRepository.findByToken("expired-token"))
                .thenReturn(Mono.just(token));

        StepVerifier.create(emailVerificationService.verify("expired-token"))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Verification token has expired");
                }).verify();

        verify(userRepository,never()).findById(anyLong());
    }

    @Test
    void resendVerification_whenUserExistsAndNotVerified_shouldGenerateToken() {
        UserCredential user=user(1L,false);

        when(userRepository.findByEmailIgnoreCase("user@test.com"))
                .thenReturn(Mono.just(user));
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(tokenRepository.findByUserIdAndVerifiedFalse(1L))
                .thenReturn(Mono.empty());
        when(tokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation->{
                    EmailVerificationToken token=invocation.getArgument(0);
                    token.setId(100L);
                    return Mono.just(token);
                });

        StepVerifier.create(emailVerificationService.resendVerification("user@test.com"))
                .assertNext(response->{
                    assertThat(response.getMessage())
                            .isEqualTo("Verification token generated");
                    assertThat(response.getToken()).isNotBlank();
                }).verifyComplete();
    }

    @Test
    void resendVerification_whenUserDoesNotExist_shouldReturnNotFoundException() {
        when(userRepository.findByEmailIgnoreCase("missing@test.com"))
                .thenReturn(Mono.empty());

        StepVerifier.create(emailVerificationService.resendVerification("missing@test.com"))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                }).verify();
    }

    @Test
    void resendVerification_whenEmailAlreadyVerified_shouldReturnConflictException() {
        UserCredential user=user(1L,true);

        when(userRepository.findByEmailIgnoreCase("user@test.com"))
                .thenReturn(Mono.just(user));

        StepVerifier.create(emailVerificationService.resendVerification("user@test.com"))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Email is already verified");
                }).verify();
    }

    private UserCredential user(Long id,boolean emailVerified) {
        LocalDateTime now=LocalDateTime.now();
        return UserCredential.builder()
                .id(id).email("user@test.com").password("encoded-password")
                .fullName("Test User").role("ROLE_CUSTOMER")
                .enabled(true).emailVerified(emailVerified)
                .createdAt(now).updatedAt(now).build();
    }

    private EmailVerificationToken existingToken(Long userId,String tokenValue,
                                                   boolean verified) {
        LocalDateTime now=LocalDateTime.now();
        return EmailVerificationToken.builder()
                .id(100L).userId(userId).token(tokenValue)
                .expiresAt(now.plusHours(24)).verified(verified)
                .createdAt(now).build();
    }
}
