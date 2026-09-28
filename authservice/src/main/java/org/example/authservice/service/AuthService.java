package org.example.authservice.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.authservice.dto.*;
import org.example.authservice.exception.AuthenticationException;
import org.example.authservice.exception.ConflictException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.exception.ValidationException;
import org.example.authservice.model.*;
import org.example.authservice.repository.*;
import org.example.authservice.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService{
    private final UserCredentialRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserProfileRepository profileRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AddressRepository addressRepository;
    private final NotificationServiceClient notificationServiceClient;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpiration;

    @Transactional
    public Mono<AuthResponse> register(
            RegisterRequest request,String deviceName,String ipAddress){

        String email=normalizeEmail(request.getEmail());

        return roleRepository.findByName("ROLE_CUSTOMER")
                .switchIfEmpty(Mono.error(new ValidationException(
                        "Default customer role not found in database"
                )))
                .flatMap(role->userRepository.existsByEmailIgnoreCase(email)
                        .flatMap(exists->{
                            if(exists){
                                return Mono.error(
                                        new ConflictException("Email is already registered")
                                );
                            }
                            return createUser(request,email,role);
                        })
                        .flatMap(user->createUserRole(user.getId(),role.getId())
                                .then(createInitialProfile(user))
                                .then(createTokens(user,deviceName,ipAddress)))
                )
                .flatMap(response->auditUser(
                        "USER_REGISTERED",response,"Registered a new account"
                ).thenReturn(response))
                .doOnNext(response->log.info("New user registered userId={}",
                        response.getUser().getId()));

    }

    private Mono<UserProfile> createInitialProfile(UserCredential user){
        LocalDateTime now=LocalDateTime.now();

        UserProfile profile=UserProfile.builder()
                .authUserId(user.getId())
                .customerSegment(CustomerSegment.INDIVIDUAL.name())
                .status(UserStatus.ACTIVE.name())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return profileRepository.save(profile);
    }

public Mono<AuthResponse> login(LoginRequest request,String deviceName,
                                      String ipAddress){
        String email=normalizeEmail(request.getEmail());

        return userRepository.findByEmailIgnoreCase(email)
                .switchIfEmpty(Mono.error(
                        new AuthenticationException("Invalid email or password")
                ))
                .flatMap(user->validateUserForLogin(user,request.getPassword()))
                .flatMap(user->createTokens(user,deviceName,ipAddress))
                .flatMap(response->auditUser(
                        "USER_LOGIN",response,
                        "Signed in"+(deviceName!=null&&!deviceName.isBlank()
                                ?" from "+deviceName
                                :"")
                ).thenReturn(response))
                .doOnNext(response->log.info("User logged in userId={} role={}",
                        response.getUser().getId(),response.getUser().getRole()));

    }

    @Transactional
    public Mono<AuthResponse> refresh(RefreshTokenRequest request,
                                      String deviceName,String ipAddress){

        return refreshTokenRepository.findByToken(request.getRefreshToken())
                .switchIfEmpty(Mono.error(
                        new AuthenticationException("Invalid refresh token")
                ))
                .flatMap(this::validateRefreshToken)
                .flatMap(oldToken->revokeRefreshToken(oldToken)
                        .then(deactivateSessionByRefreshToken(oldToken.getId()))
                        .then(userRepository.findById(oldToken.getUserId()))
                )
                .switchIfEmpty(Mono.error(
                        new AuthenticationException("User not found")
                ))
                .flatMap(user->{
                    if(!Boolean.TRUE.equals(user.getEnabled())){
                        return Mono.error(new AuthenticationException(
                                "Account is disabled"
                        ));
                    }
                    log.debug("Refreshing tokens for userId={}",user.getId());
                    return createTokens(user,deviceName,ipAddress);
                });

    }

    @Transactional
    public Mono<MessageResponse> logout(Long userId,RefreshTokenRequest request){
        return refreshTokenRepository.findByToken(request.getRefreshToken())
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Active session not found")
                ))
                .filter(token->token.getUserId().equals(userId))
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Active session not found")
                ))
                .flatMap(token->revokeRefreshToken(token)
                        .then(deactivateSessionByRefreshToken(token.getId()))
                )
                .then(notificationServiceClient.audit(
                        "USER_LOGOUT","USER",userId,userId,null,"Signed out"
                ))
                .doOnSuccess(ignored->log.info("User logged out userId={}",userId))
                .thenReturn(MessageResponse.builder()
                        .message("Logged out successfully")
                        .build());
    }

    public Mono<UserResponse> getCurrentUser(Long userId){
        log.debug("Get current user [auth]: userId={}",userId);
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("User not found")
                ))
                .flatMap(user->profileRepository.findByAuthUserId(userId)
                        .flatMap(profile->addressRepository
                                .findAllByUserProfileId(profile.getId())
                                .collectList()
                                .map(addresses->buildFullUserResponse(
                                        user,profile,addresses)))
                        .switchIfEmpty(Mono.defer(()->{
                            UserResponse response=toUserResponse(user);
                            return Mono.just(response);
                        })));
    }

    public Flux<SessionResponse> getSessions(Long userId){
        log.debug("Get sessions [auth]: userId={}",userId);
        return sessionRepository.findAllByUserIdAndActiveTrue(userId)
                .map(this::toSessionResponse);
    }

    @Transactional
    public Mono<MessageResponse> revokeSession(Long userId,Long sessionId){
        return sessionRepository.findByIdAndUserId(sessionId,userId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Session not found")
                ))
                .flatMap(session->{
                    session.setActive(false);
                    session.setLastAccessedAt(LocalDateTime.now());

                    Mono<Void> revokeToken=session.getRefreshTokenId()==null
                            ?Mono.empty()
                            :refreshTokenRepository.findById(
                                    session.getRefreshTokenId())
                            .flatMap(this::revokeRefreshToken)
                            .then();

                    return sessionRepository.save(session)
                            .then(revokeToken);
                })
                .doOnSuccess(ignored->log.info(
                        "Session {} revoked for userId={}",sessionId,userId))
                .thenReturn(MessageResponse.builder()
                        .message("Session revoked successfully")
                        .build());
    }

    private Mono<UserCredential> createUser(RegisterRequest request,
                                             String email,Role role){
        LocalDateTime now=LocalDateTime.now();

        UserCredential user=UserCredential.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role.getName())
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return userRepository.save(user);
    }

    private Mono<Void> createUserRole(Long userId,Long roleId){
        UserRole userRole=UserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .build();
        return userRoleRepository.save(userRole).then();
    }

    private Mono<UserCredential> validateUserForLogin(UserCredential user,
                                                      String password){
        if(!Boolean.TRUE.equals(user.getEnabled())){
            log.warn("Login blocked for disabled account userId={}",user.getId());
            return Mono.error(new AuthenticationException("Account is disabled"));
        }

        if(!passwordEncoder.matches(password,user.getPassword())){
            log.warn("Failed login attempt for userId={}",user.getId());
            return Mono.error(new AuthenticationException(
                    "Invalid email or password"
            ));
        }

        return Mono.just(user);
    }

    private Mono<RefreshToken> validateRefreshToken(RefreshToken token){
        if(Boolean.TRUE.equals(token.getRevoked())){
            log.warn("Revoked refresh token used by userId={}",token.getUserId());
            return Mono.error(new AuthenticationException(
                    "Refresh token is revoked"
            ));
        }

        if(token.getExpiresAt()==null||
                token.getExpiresAt().isBefore(LocalDateTime.now())){
            return Mono.error(new AuthenticationException(
                    "Refresh token is expired"
            ));
        }

        return Mono.just(token);
    }

    private Mono<AuthResponse> createTokens(UserCredential user,
                                             String deviceName,String ipAddress){
        String accessToken=jwtUtil.generateToken(user,user.getId());
        String refreshTokenValue=generateRefreshToken();
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime expiresAt=now.plusNanos(refreshExpiration*1_000_000);

        RefreshToken refreshToken=RefreshToken.builder()
                .userId(user.getId())
                .token(refreshTokenValue)
                .expiresAt(expiresAt)
                .revoked(false)
                .createdAt(now)
                .build();

        return refreshTokenRepository.save(refreshToken)
                .flatMap(savedToken->createSession(
                        user.getId(),
                        savedToken.getId(),
                        deviceName,
                        ipAddress,
                        now
                ).thenReturn(AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(savedToken.getToken())
                        .tokenType("Bearer")
                        .expiresIn(jwtUtil.getExpirationSeconds())
                        .user(toUserResponse(user))
                        .build()
                ));
    }

    private Mono<Session> createSession(Long userId,Long refreshTokenId,
                                         String deviceName,String ipAddress,
                                         LocalDateTime createdAt){
        Session session=Session.builder()
                .userId(userId)
                .refreshTokenId(refreshTokenId)
                .deviceName(normalizeDeviceName(deviceName))
                .ipAddress(normalizeIpAddress(ipAddress))
                .active(true)
                .createdAt(createdAt)
                .lastAccessedAt(createdAt)
                .build();

        return sessionRepository.save(session);
    }

    private Mono<RefreshToken> revokeRefreshToken(RefreshToken token){
        token.setRevoked(true);
        return refreshTokenRepository.save(token);
    }

    private Mono<Void> deactivateSessionByRefreshToken(Long refreshTokenId){
        return sessionRepository.findByRefreshTokenId(refreshTokenId)
                .flatMap(session->{
                    session.setActive(false);
                    session.setLastAccessedAt(LocalDateTime.now());
                    return sessionRepository.save(session);
                })
                .then();
    }

    private UserResponse buildFullUserResponse(UserCredential user,
                                                UserProfile profile,
                                                List<Address> addresses){
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .phoneNumber(profile.getPhoneNumber())
                .dateOfBirth(profile.getDateOfBirth())
                .customerSegment(profile.getCustomerSegment())
                .profileStatus(profile.getStatus())
                .addresses(addresses.stream()
                        .map(this::toAddressResponse)
                        .toList())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private UserResponse toUserResponse(UserCredential user){
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .build();
    }

    private AddressResponse toAddressResponse(Address address){
        return AddressResponse.builder()
                .id(address.getId())
                .addressLineOne(address.getAddressLineOne())
                .addressLineTwo(address.getAddressLineTwo())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .primaryAddress(address.getPrimaryAddress())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }

    private SessionResponse toSessionResponse(Session session){
        return SessionResponse.builder()
                .id(session.getId())
                .deviceName(session.getDeviceName())
                .ipAddress(session.getIpAddress())
                .active(session.getActive())
                .createdAt(session.getCreatedAt())
                .lastAccessedAt(session.getLastAccessedAt())
                .build();
    }

    private String generateRefreshToken(){
        return UUID.randomUUID()+"."+UUID.randomUUID();
    }

    private String normalizeEmail(String email){
        return email.trim().toLowerCase();
    }

    private String normalizeDeviceName(String deviceName){
        if(deviceName==null||deviceName.isBlank()) return "Unknown device";
        return deviceName.length()>150
                ?deviceName.substring(0,150):deviceName;
    }

    private String normalizeIpAddress(String ipAddress){
        if(ipAddress==null||ipAddress.isBlank()) return "Unknown";
        return ipAddress.length()>100
                ?ipAddress.substring(0,100):ipAddress;
    }

    private Mono<Void> auditUser(String eventType,AuthResponse response,String description){
        UserResponse user=response.getUser();
        if(user==null||user.getId()==null){
            return Mono.empty();
        }
        return notificationServiceClient.audit(
                eventType,"USER",user.getId(),user.getId(),user.getRole(),description
        );
    }
}
