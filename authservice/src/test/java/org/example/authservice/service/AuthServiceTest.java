package org.example.authservice.service;

import org.example.authservice.dto.*;
import org.example.authservice.exception.AuthenticationException;
import org.example.authservice.exception.ConflictException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.model.*;
import org.example.authservice.repository.*;
import org.example.authservice.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest{
    @Mock private UserCredentialRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserProfileRepository profileRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private NotificationServiceClient notificationServiceClient;

    private AuthService authService;

    @BeforeEach
    void setUp(){
        authService=new AuthService(
                userRepository,refreshTokenRepository,sessionRepository,
                passwordEncoder,jwtUtil,profileRepository,
                roleRepository,userRoleRepository,addressRepository,
                notificationServiceClient
        );
        lenient().when(notificationServiceClient.audit(any(),any(),any(),any(),any(),any()))
                .thenReturn(reactor.core.publisher.Mono.empty());
        ReflectionTestUtils.setField(authService,"refreshExpiration",604_800_000L);
    }

    @Test
    void register_whenEmailIsNew_shouldCreateUserProfileTokensAndSession(){
        RegisterRequest request=mock(RegisterRequest.class);
        Role customerRole=Role.builder().id(1L).name("ROLE_CUSTOMER").build();

        when(request.getEmail()).thenReturn("  AKSHAYA@TEST.COM  ");
        when(request.getPassword()).thenReturn("Password@123");
        when(request.getFullName()).thenReturn("  Akshaya Chigullapally  ");
        when(roleRepository.findByName("ROLE_CUSTOMER"))
                .thenReturn(Mono.just(customerRole));
        when(userRepository.existsByEmailIgnoreCase("akshaya@test.com"))
                .thenReturn(Mono.just(false));
        when(passwordEncoder.encode("Password@123"))
                .thenReturn("encoded-password");
        when(userRepository.save(any(UserCredential.class)))
                .thenAnswer(invocation->{
                    UserCredential user=invocation.getArgument(0);
                    user.setId(1L);
                    return Mono.just(user);
                });
        when(userRoleRepository.save(any(UserRole.class)))
                .thenAnswer(invocation->{
                    UserRole ur=invocation.getArgument(0);
                    ur.setId(1L);
                    return Mono.just(ur);
                });
        when(profileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation->{
                    UserProfile profile=invocation.getArgument(0);
                    profile.setId(10L);
                    return Mono.just(profile);
                });
        when(jwtUtil.generateToken(any(UserCredential.class),any()))
                .thenReturn("access-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(3600L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation->{
                    RefreshToken token=invocation.getArgument(0);
                    token.setId(100L);
                    return Mono.just(token);
                });
        when(sessionRepository.save(any(Session.class)))
                .thenAnswer(invocation->{
                    Session session=invocation.getArgument(0);
                    session.setId(1000L);
                    return Mono.just(session);
                });

        StepVerifier.create(
                authService.register(request,"Chrome","127.0.0.1"))
                .assertNext(response->{
                    assertThat(response.getAccessToken())
                            .isEqualTo("access-token");
                    assertThat(response.getRefreshToken()).isNotBlank();
                    assertThat(response.getTokenType()).isEqualTo("Bearer");
                    assertThat(response.getExpiresIn()).isEqualTo(3600L);
                    assertThat(response.getUser().getId()).isEqualTo(1L);
                    assertThat(response.getUser().getEmail())
                            .isEqualTo("akshaya@test.com");
                    assertThat(response.getUser().getFullName())
                            .isEqualTo("Akshaya Chigullapally");
                    assertThat(response.getUser().getRole())
                            .isEqualTo("ROLE_CUSTOMER");
                })
                .verifyComplete();

        ArgumentCaptor<UserCredential> userCaptor=
                ArgumentCaptor.forClass(UserCredential.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword())
                .isEqualTo("encoded-password");
        assertThat(userCaptor.getValue().getEnabled()).isTrue();
        assertThat(userCaptor.getValue().getEmailVerified()).isFalse();

        verify(roleRepository).findByName("ROLE_CUSTOMER");
        verify(userRoleRepository).save(any(UserRole.class));

        ArgumentCaptor<UserProfile> profileCaptor=
                ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getAuthUserId()).isEqualTo(1L);
        assertThat(profileCaptor.getValue().getCustomerSegment())
                .isEqualTo("INDIVIDUAL");
        assertThat(profileCaptor.getValue().getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<Session> sessionCaptor=
                ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(sessionCaptor.getValue().getRefreshTokenId())
                .isEqualTo(100L);
        assertThat(sessionCaptor.getValue().getDeviceName())
                .isEqualTo("Chrome");
        assertThat(sessionCaptor.getValue().getIpAddress())
                .isEqualTo("127.0.0.1");
        assertThat(sessionCaptor.getValue().getActive()).isTrue();
    }

    @Test
    void register_whenEmailAlreadyExists_shouldReturnConflictException(){
        RegisterRequest request=mock(RegisterRequest.class);
        Role customerRole=Role.builder().id(1L).name("ROLE_CUSTOMER").build();

        when(request.getEmail()).thenReturn("Existing@Test.com");
        when(roleRepository.findByName("ROLE_CUSTOMER"))
                .thenReturn(Mono.just(customerRole));
        when(userRepository.existsByEmailIgnoreCase("existing@test.com"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(
                authService.register(request,"Chrome","127.0.0.1"))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Email is already registered");
                })
                .verify();

        verify(userRepository,never()).save(any());
        verify(profileRepository,never()).save(any());
        verify(refreshTokenRepository,never()).save(any());
        verify(sessionRepository,never()).save(any());
    }

    @Test
    void login_whenCredentialsAreValid_shouldReturnTokens(){
        LoginRequest request=mock(LoginRequest.class);
        UserCredential user=user(true);

        when(request.getEmail()).thenReturn("  USER@TEST.COM ");
        when(request.getPassword()).thenReturn("Password@123");
        when(userRepository.findByEmailIgnoreCase("user@test.com"))
                .thenReturn(Mono.just(user));
        when(passwordEncoder.matches("Password@123","encoded-password"))
                .thenReturn(true);
        when(jwtUtil.generateToken(user,user.getId())).thenReturn("access-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(3600L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation->{
                    RefreshToken token=invocation.getArgument(0);
                    token.setId(100L);
                    return Mono.just(token);
                });
        when(sessionRepository.save(any(Session.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(
                authService.login(request,"Mobile","10.0.0.1"))
                .assertNext(response->{
                    assertThat(response.getAccessToken())
                            .isEqualTo("access-token");
                    assertThat(response.getRefreshToken()).isNotBlank();
                    assertThat(response.getUser().getEmail())
                            .isEqualTo("user@test.com");
                })
                .verifyComplete();

        verify(passwordEncoder).matches("Password@123","encoded-password");
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    void login_whenUserDoesNotExist_shouldReturnAuthenticationException(){
        LoginRequest request=mock(LoginRequest.class);
        when(request.getEmail()).thenReturn("missing@test.com");
        when(userRepository.findByEmailIgnoreCase("missing@test.com"))
                .thenReturn(Mono.empty());

        StepVerifier.create(
                authService.login(request,"Chrome","127.0.0.1"))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Invalid email or password");
                })
                .verify();

        verify(passwordEncoder,never()).matches(anyString(),anyString());
        verify(refreshTokenRepository,never()).save(any());
    }

    @Test
    void login_whenPasswordIsIncorrect_shouldReturnAuthenticationException(){
        LoginRequest request=mock(LoginRequest.class);
        UserCredential user=user(true);

        when(request.getEmail()).thenReturn("user@test.com");
        when(request.getPassword()).thenReturn("wrong-password");
        when(userRepository.findByEmailIgnoreCase("user@test.com"))
                .thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong-password","encoded-password"))
                .thenReturn(false);

        StepVerifier.create(
                authService.login(request,"Chrome","127.0.0.1"))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Invalid email or password");
                })
                .verify();

        verify(refreshTokenRepository,never()).save(any());
        verify(sessionRepository,never()).save(any());
    }

    @Test
    void login_whenAccountIsDisabled_shouldReturnAuthenticationException(){
        LoginRequest request=mock(LoginRequest.class);
        when(request.getEmail()).thenReturn("user@test.com");
        when(userRepository.findByEmailIgnoreCase("user@test.com"))
                .thenReturn(Mono.just(user(false)));

        StepVerifier.create(
                authService.login(request,"Chrome","127.0.0.1"))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Account is disabled");
                })
                .verify();

        verify(passwordEncoder,never()).matches(anyString(),anyString());
        verify(refreshTokenRepository,never()).save(any());
    }

    @Test
    void refresh_whenTokenIsValid_shouldRevokeOldTokenAndCreateNewTokens(){
        RefreshTokenRequest request=mock(RefreshTokenRequest.class);
        RefreshToken oldToken=refreshToken(
                false,LocalDateTime.now().plusDays(1));
        Session oldSession=session(true);
        UserCredential user=user(true);

        when(request.getRefreshToken()).thenReturn("old-refresh-token");
        when(refreshTokenRepository.findByToken("old-refresh-token"))
                .thenReturn(Mono.just(oldToken));
        when(refreshTokenRepository.save(oldToken))
                .thenReturn(Mono.just(oldToken));
        when(sessionRepository.findByRefreshTokenId(100L))
                .thenReturn(Mono.just(oldSession));
        when(sessionRepository.save(oldSession))
                .thenReturn(Mono.just(oldSession));
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(jwtUtil.generateToken(user,user.getId()))
                .thenReturn("new-access-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(3600L);
        when(refreshTokenRepository.save(
                argThat(token->token!=oldToken)))
                .thenAnswer(invocation->{
                    RefreshToken token=invocation.getArgument(0);
                    token.setId(200L);
                    return Mono.just(token);
                });
        when(sessionRepository.save(
                argThat(value->value!=oldSession)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(
                authService.refresh(request,"Edge","10.0.0.2"))
                .assertNext(response->{
                    assertThat(response.getAccessToken())
                            .isEqualTo("new-access-token");
                    assertThat(response.getRefreshToken())
                            .isNotEqualTo("old-refresh-token");
                    assertThat(response.getTokenType()).isEqualTo("Bearer");
                })
                .verifyComplete();

        assertThat(oldToken.getRevoked()).isTrue();
        assertThat(oldSession.getActive()).isFalse();
        verify(refreshTokenRepository,times(2)).save(any(RefreshToken.class));
        verify(sessionRepository,times(2)).save(any(Session.class));
    }

    @Test
    void refresh_whenTokenDoesNotExist_shouldReturnAuthException(){
        RefreshTokenRequest request=mock(RefreshTokenRequest.class);
        when(request.getRefreshToken()).thenReturn("missing-token");
        when(refreshTokenRepository.findByToken("missing-token"))
                .thenReturn(Mono.empty());

        StepVerifier.create(
                authService.refresh(request,"Chrome","127.0.0.1"))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Invalid refresh token");
                })
                .verify();
    }

    @Test
    void refresh_whenTokenIsRevoked_shouldReturnAuthException(){
        RefreshTokenRequest request=mock(RefreshTokenRequest.class);
        when(request.getRefreshToken()).thenReturn("revoked-token");
        when(refreshTokenRepository.findByToken("revoked-token"))
                .thenReturn(Mono.just(refreshToken(
                        true,LocalDateTime.now().plusDays(1))));

        StepVerifier.create(
                authService.refresh(request,"Chrome","127.0.0.1"))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Refresh token is revoked");
                })
                .verify();

        verify(refreshTokenRepository,never()).save(any());
    }

    @Test
    void refresh_whenTokenIsExpired_shouldReturnAuthException(){
        RefreshTokenRequest request=mock(RefreshTokenRequest.class);
        when(request.getRefreshToken()).thenReturn("expired-token");
        when(refreshTokenRepository.findByToken("expired-token"))
                .thenReturn(Mono.just(refreshToken(
                        false,LocalDateTime.now().minusMinutes(1))));

        StepVerifier.create(
                authService.refresh(request,"Chrome","127.0.0.1"))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Refresh token is expired");
                })
                .verify();

        verify(refreshTokenRepository,never()).save(any());
    }

    @Test
    void logout_whenTokenBelongsToUser_shouldRevokeAndDeactivate(){
        RefreshTokenRequest request=mock(RefreshTokenRequest.class);
        RefreshToken token=refreshToken(
                false,LocalDateTime.now().plusDays(1));
        Session session=session(true);

        when(request.getRefreshToken()).thenReturn("refresh-token");
        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Mono.just(token));
        when(refreshTokenRepository.save(token))
                .thenReturn(Mono.just(token));
        when(sessionRepository.findByRefreshTokenId(100L))
                .thenReturn(Mono.just(session));
        when(sessionRepository.save(session))
                .thenReturn(Mono.just(session));

        StepVerifier.create(authService.logout(1L,request))
                .assertNext(response->assertThat(response.getMessage())
                        .isEqualTo("Logged out successfully"))
                .verifyComplete();

        assertThat(token.getRevoked()).isTrue();
        assertThat(session.getActive()).isFalse();
    }

    @Test
    void logout_whenTokenBelongsToDifferentUser_shouldReturnNotFound(){
        RefreshTokenRequest request=mock(RefreshTokenRequest.class);
        RefreshToken token=refreshToken(
                false,LocalDateTime.now().plusDays(1));
        token.setUserId(2L);

        when(request.getRefreshToken()).thenReturn("refresh-token");
        when(refreshTokenRepository.findByToken("refresh-token"))
                .thenReturn(Mono.just(token));

        StepVerifier.create(authService.logout(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Active session not found");
                })
                .verify();

        verify(refreshTokenRepository,never()).save(any());
        verify(sessionRepository,never()).save(any());
    }

    @Test
    void getCurrentUser_whenUserExists_shouldReturnFullResponse(){
        UserCredential user=user(true);
        UserProfile profile=UserProfile.builder()
                .id(10L).authUserId(1L)
                .phoneNumber("9876543210")
                .customerSegment("INDIVIDUAL")
                .status("ACTIVE")
                .build();
        Address address=Address.builder()
                .id(5L).userProfileId(10L)
                .addressLineOne("123 Main St")
                .city("Hartford").state("CT")
                .postalCode("06103").country("US")
                .primaryAddress(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(profileRepository.findByAuthUserId(1L))
                .thenReturn(Mono.just(profile));
        when(addressRepository.findAllByUserProfileId(10L))
                .thenReturn(Flux.just(address));

        StepVerifier.create(authService.getCurrentUser(1L))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getFullName())
                            .isEqualTo("Test User");
                    assertThat(response.getEmail())
                            .isEqualTo("user@test.com");
                    assertThat(response.getRole())
                            .isEqualTo("ROLE_CUSTOMER");
                    assertThat(response.getEmailVerified()).isFalse();
                    assertThat(response.getPhoneNumber())
                            .isEqualTo("9876543210");
                    assertThat(response.getCustomerSegment())
                            .isEqualTo("INDIVIDUAL");
                    assertThat(response.getProfileStatus())
                            .isEqualTo("ACTIVE");
                    assertThat(response.getAddresses()).hasSize(1);
                    assertThat(response.getAddresses().get(0).getCity())
                            .isEqualTo("Hartford");
                })
                .verifyComplete();
    }

    @Test
    void getCurrentUser_whenUserDoesNotExist_shouldReturnNotFound(){
        when(userRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(authService.getCurrentUser(99L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("User not found");
                })
                .verify();
    }

    @Test
    void getSessions_whenActiveExist_shouldReturnSessionResponses(){
        Session first=session(true);
        first.setId(10L);
        first.setDeviceName("Chrome");
        Session second=session(true);
        second.setId(20L);
        second.setDeviceName("Mobile");

        when(sessionRepository.findAllByUserIdAndActiveTrue(1L))
                .thenReturn(Flux.just(first,second));

        StepVerifier.create(authService.getSessions(1L))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(10L);
                    assertThat(response.getDeviceName()).isEqualTo("Chrome");
                    assertThat(response.getActive()).isTrue();
                })
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(20L);
                    assertThat(response.getDeviceName()).isEqualTo("Mobile");
                    assertThat(response.getActive()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void revokeSession_whenExists_shouldDeactivateAndRevokeToken(){
        Session session=session(true);
        RefreshToken token=refreshToken(
                false,LocalDateTime.now().plusDays(1));

        when(sessionRepository.findByIdAndUserId(10L,1L))
                .thenReturn(Mono.just(session));
        when(sessionRepository.save(session))
                .thenReturn(Mono.just(session));
        when(refreshTokenRepository.findById(100L))
                .thenReturn(Mono.just(token));
        when(refreshTokenRepository.save(token))
                .thenReturn(Mono.just(token));

        StepVerifier.create(authService.revokeSession(1L,10L))
                .assertNext(response->assertThat(response.getMessage())
                        .isEqualTo("Session revoked successfully"))
                .verifyComplete();

        assertThat(session.getActive()).isFalse();
        assertThat(session.getLastAccessedAt()).isNotNull();
        assertThat(token.getRevoked()).isTrue();
    }

    @Test
    void revokeSession_whenNoRefreshToken_shouldOnlyDeactivate(){
        Session session=session(true);
        session.setRefreshTokenId(null);

        when(sessionRepository.findByIdAndUserId(10L,1L))
                .thenReturn(Mono.just(session));
        when(sessionRepository.save(session))
                .thenReturn(Mono.just(session));

        StepVerifier.create(authService.revokeSession(1L,10L))
                .assertNext(response->assertThat(response.getMessage())
                        .isEqualTo("Session revoked successfully"))
                .verifyComplete();

        assertThat(session.getActive()).isFalse();
        verify(refreshTokenRepository,never()).findById(anyLong());
        verify(refreshTokenRepository,never()).save(any());
    }

    @Test
    void revokeSession_whenNotExists_shouldReturnNotFound(){
        when(sessionRepository.findByIdAndUserId(99L,1L))
                .thenReturn(Mono.empty());

        StepVerifier.create(authService.revokeSession(1L,99L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Session not found");
                })
                .verify();
    }

    @Test
    void login_whenDeviceAndIpAreBlank_shouldStoreDefaults(){
        LoginRequest request=mock(LoginRequest.class);
        UserCredential user=user(true);

        when(request.getEmail()).thenReturn("user@test.com");
        when(request.getPassword()).thenReturn("Password@123");
        when(userRepository.findByEmailIgnoreCase("user@test.com"))
                .thenReturn(Mono.just(user));
        when(passwordEncoder.matches("Password@123","encoded-password"))
                .thenReturn(true);
        when(jwtUtil.generateToken(user,user.getId()))
                .thenReturn("access-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(3600L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation->{
                    RefreshToken token=invocation.getArgument(0);
                    token.setId(100L);
                    return Mono.just(token);
                });
        when(sessionRepository.save(any(Session.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(authService.login(request,"  "," "))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Session> captor=
                ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getDeviceName())
                .isEqualTo("Unknown device");
        assertThat(captor.getValue().getIpAddress()).isEqualTo("Unknown");
    }

    private UserCredential user(boolean enabled){
        return UserCredential.builder()
                .id(1L).email("user@test.com")
                .password("encoded-password")
                .fullName("Test User").role("ROLE_CUSTOMER")
                .enabled(enabled).emailVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now()).build();
    }

    private RefreshToken refreshToken(boolean revoked,
                                       LocalDateTime expiresAt){
        return RefreshToken.builder()
                .id(100L).userId(1L).token("refresh-token")
                .expiresAt(expiresAt).revoked(revoked)
                .createdAt(LocalDateTime.now()).build();
    }

    private Session session(boolean active){
        LocalDateTime now=LocalDateTime.now();
        return Session.builder()
                .id(10L).userId(1L).refreshTokenId(100L)
                .deviceName("Chrome").ipAddress("127.0.0.1")
                .active(active).createdAt(now)
                .lastAccessedAt(now).build();
    }
}
