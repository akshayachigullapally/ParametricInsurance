package org.example.authservice.service;

import org.example.authservice.exception.AuthenticationException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.model.PasswordResetToken;
import org.example.authservice.model.UserCredential;
import org.example.authservice.repository.PasswordResetTokenRepository;
import org.example.authservice.repository.UserCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private UserCredentialRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService=new PasswordResetService(
                tokenRepository,userRepository,passwordEncoder
        );
        ReflectionTestUtils.setField(passwordResetService,"tokenExpirationHours",1L);
    }

    @Test
    void generateToken_whenUserExists_shouldCreateTokenAndReturnResponse() {
        UserCredential user=user(1L,"user@test.com");

        when(userRepository.findByEmailIgnoreCase("user@test.com"))
                .thenReturn(Mono.just(user));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation->{
                    PasswordResetToken token=invocation.getArgument(0);
                    token.setId(100L);
                    return Mono.just(token);
                });

        StepVerifier.create(passwordResetService.generateToken("user@test.com"))
                .assertNext(response->{
                    assertThat(response.getMessage())
                            .isEqualTo("Password reset token generated");
                    assertThat(response.getToken()).isNotBlank();
                }).verifyComplete();

        ArgumentCaptor<PasswordResetToken> captor=
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getRevoked()).isFalse();
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void generateToken_whenUserDoesNotExist_shouldReturnNotFoundException() {
        when(userRepository.findByEmailIgnoreCase("missing@test.com"))
                .thenReturn(Mono.empty());

        StepVerifier.create(passwordResetService.generateToken("missing@test.com"))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                }).verify();

        verify(tokenRepository,never()).save(any());
    }

    @Test
    void resetPassword_whenTokenIsValid_shouldRevokeTokenAndUpdatePassword() {
        PasswordResetToken token=resetToken(1L,"valid-token",false);
        UserCredential user=user(1L,"user@test.com");

        when(tokenRepository.findByToken("valid-token"))
                .thenReturn(Mono.just(token));
        when(tokenRepository.save(token)).thenReturn(Mono.just(token));
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(passwordEncoder.encode("NewPassword@123"))
                .thenReturn("new-encoded-password");
        when(userRepository.save(user)).thenReturn(Mono.just(user));

        StepVerifier.create(passwordResetService.resetPassword("valid-token","NewPassword@123"))
                .assertNext(response->assertThat(response.getMessage())
                        .isEqualTo("Password reset successfully"))
                .verifyComplete();

        assertThat(token.getRevoked()).isTrue();
        assertThat(user.getPassword()).isEqualTo("new-encoded-password");
    }

    @Test
    void resetPassword_whenTokenDoesNotExist_shouldReturnAuthenticationException() {
        when(tokenRepository.findByToken("missing-token"))
                .thenReturn(Mono.empty());

        StepVerifier.create(passwordResetService.resetPassword("missing-token","NewPassword@123"))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Invalid reset token");
                }).verify();

        verify(userRepository,never()).findById(anyLong());
        verify(passwordEncoder,never()).encode(anyString());
    }

    @Test
    void resetPassword_whenTokenIsRevoked_shouldReturnAuthenticationException() {
        PasswordResetToken token=resetToken(1L,"revoked-token",true);

        when(tokenRepository.findByToken("revoked-token"))
                .thenReturn(Mono.just(token));

        StepVerifier.create(passwordResetService.resetPassword("revoked-token","NewPassword@123"))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Reset token has been revoked");
                }).verify();

        verify(userRepository,never()).findById(anyLong());
    }

    @Test
    void resetPassword_whenTokenIsExpired_shouldReturnAuthenticationException() {
        PasswordResetToken token=resetToken(1L,"expired-token",false);
        token.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(tokenRepository.findByToken("expired-token"))
                .thenReturn(Mono.just(token));

        StepVerifier.create(passwordResetService.resetPassword("expired-token","NewPassword@123"))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Reset token has expired");
                }).verify();

        verify(userRepository,never()).findById(anyLong());
    }

    private UserCredential user(Long id,String email) {
        LocalDateTime now=LocalDateTime.now();
        return UserCredential.builder()
                .id(id).email(email).password("encoded-password")
                .fullName("Test User").role("ROLE_CUSTOMER")
                .enabled(true).emailVerified(false)
                .createdAt(now).updatedAt(now).build();
    }

    private PasswordResetToken resetToken(Long userId,String tokenValue,
                                           boolean revoked) {
        LocalDateTime now=LocalDateTime.now();
        return PasswordResetToken.builder()
                .id(100L).userId(userId).token(tokenValue)
                .expiresAt(now.plusHours(1)).revoked(revoked)
                .createdAt(now).build();
    }
}
