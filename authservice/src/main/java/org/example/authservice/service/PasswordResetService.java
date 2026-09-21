package org.example.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.authservice.dto.MessageResponse;
import org.example.authservice.exception.AuthenticationException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.model.PasswordResetToken;
import org.example.authservice.model.UserCredential;
import org.example.authservice.repository.PasswordResetTokenRepository;
import org.example.authservice.repository.UserCredentialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService{
    private final PasswordResetTokenRepository tokenRepository;
    private final UserCredentialRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${password.reset-token-expiration-hours:1}")
    private long tokenExpirationHours;

    @Transactional
    public Mono<MessageResponse> generateToken(String email){
        String normalizedEmail=email.trim().toLowerCase();

        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                .flatMap(user->{
                    PasswordResetToken token=PasswordResetToken.builder()
                            .userId(user.getId())
                            .token(UUID.randomUUID().toString())
                            .expiresAt(LocalDateTime.now().plusHours(tokenExpirationHours))
                            .revoked(false)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return tokenRepository.save(token)
                            .map(saved->{
                                return MessageResponse.builder()
                                        .message("Password reset token generated")
                                        .token(saved.getToken())
                                        .build();
                            });
                });
    }

    @Transactional
    public Mono<MessageResponse> resetPassword(String tokenValue,
                                                String newPassword){
        return tokenRepository.findByToken(tokenValue)
                .switchIfEmpty(Mono.error(
                        new AuthenticationException("Invalid reset token")
                ))
                .flatMap(token->{
                    if(Boolean.TRUE.equals(token.getRevoked())){
                        return Mono.error(new AuthenticationException(
                                "Reset token has been revoked"
                        ));
                    }

                    if(token.getExpiresAt()==null||
                            token.getExpiresAt().isBefore(LocalDateTime.now())){
                        return Mono.error(new AuthenticationException(
                                "Reset token has expired"
                        ));
                    }

                    token.setRevoked(true);
                    return tokenRepository.save(token)
                            .flatMap(saved->userRepository.findById(saved.getUserId()))
                            .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                            .flatMap(user->{
                                user.setPassword(passwordEncoder.encode(newPassword));
                                user.setUpdatedAt(LocalDateTime.now());
                                return userRepository.save(user);
                            })
                            .thenReturn(MessageResponse.builder()
                                    .message("Password reset successfully")
                                    .build());
                });
    }
}