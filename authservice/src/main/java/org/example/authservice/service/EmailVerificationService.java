package org.example.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.authservice.dto.MessageResponse;
import org.example.authservice.exception.AuthenticationException;
import org.example.authservice.exception.ConflictException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.model.EmailVerificationToken;
import org.example.authservice.model.UserCredential;
import org.example.authservice.repository.EmailVerificationTokenRepository;
import org.example.authservice.repository.UserCredentialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService{
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserCredentialRepository userRepository;

    @Value("${email.verification-token-expiration-hours:24}")
    private long tokenExpirationHours;

    @Transactional
    public Mono<MessageResponse> generateToken(Long userId){
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                .flatMap(user->{
                    if(Boolean.TRUE.equals(user.getEmailVerified())){
                        return Mono.error(new ConflictException("Email is already verified"));
                    }

                    return tokenRepository.findByUserIdAndVerifiedFalse(userId)
                            .flatMap(existing->{
                                existing.setToken(UUID.randomUUID().toString());
                                existing.setExpiresAt(
                                        LocalDateTime.now().plusHours(tokenExpirationHours)
                                );
                                return tokenRepository.save(existing);
                            })
                            .switchIfEmpty(Mono.defer(()->{
                                EmailVerificationToken token=EmailVerificationToken.builder()
                                        .userId(userId)
                                        .token(UUID.randomUUID().toString())
                                        .expiresAt(LocalDateTime.now().plusHours(tokenExpirationHours))
                                        .verified(false)
                                        .createdAt(LocalDateTime.now())
                                        .build();
                                return tokenRepository.save(token);
                            }))
                            .map(saved->{
                                log.info("Email verification token for user {}: {}",
                                        userId,saved.getToken());
                                return MessageResponse.builder()
                                        .message("Verification token generated")
                                        .token(saved.getToken())
                                        .build();
                            });
                });
    }

    @Transactional
    public Mono<MessageResponse> verify(String tokenValue){
        return tokenRepository.findByToken(tokenValue)
                .switchIfEmpty(Mono.error(
                        new AuthenticationException("Invalid verification token")
                ))
                .flatMap(token->{
                    if(Boolean.TRUE.equals(token.getVerified())){
                        return Mono.error(new ConflictException("Email is already verified"));
                    }

                    if(token.getExpiresAt()==null||
                            token.getExpiresAt().isBefore(LocalDateTime.now())){
                        return Mono.error(new AuthenticationException(
                                "Verification token has expired"
                        ));
                    }

                    token.setVerified(true);
                    return tokenRepository.save(token)
                            .flatMap(saved->userRepository.findById(saved.getUserId()))
                            .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                            .flatMap(user->{
                                user.setEmailVerified(true);
                                user.setUpdatedAt(LocalDateTime.now());
                                return userRepository.save(user);
                            })
                            .thenReturn(MessageResponse.builder()
                                    .message("Email verified successfully")
                                    .build());
                });
    }

    @Transactional
    public Mono<MessageResponse> resendVerification(String email){
        String normalizedEmail=email.trim().toLowerCase();

        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                .flatMap(user->{
                    if(Boolean.TRUE.equals(user.getEmailVerified())){
                        return Mono.error(new ConflictException("Email is already verified"));
                    }

                    return generateToken(user.getId());
                });
    }
}