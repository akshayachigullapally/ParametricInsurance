package org.example.authservice.handler;

import jakarta.validation.Validator;
import org.example.authservice.dto.*;
import org.example.authservice.exception.AuthenticationException;
import org.example.authservice.exception.ConflictException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.exception.ValidationException;
import org.example.authservice.service.AuthService;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthHandlerTest {

    @Mock
    private AuthService authService;

    private Validator validator;
    private AuthHandler authHandler;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.afterPropertiesSet();
        validator = factory;
        authHandler = new AuthHandler(authService, validator);
    }

    // ======================== register ========================

    @Test
    void register_whenValidRequest_shouldReturn201() {
        AuthResponse response = authResponse();
        when(authService.register(any(RegisterRequest.class), eq("Chrome"), eq("127.0.0.1")))
                .thenReturn(Mono.just(response));

        ServerRequest request = requestWithBody(
                RegisterRequest.builder().fullName("Test User")
                        .email("test@test.com").password("Password@123").build(),
                "Chrome", "127.0.0.1", null, null);

        StepVerifier.create(authHandler.register(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();

        verify(authService).register(any(RegisterRequest.class), eq("Chrome"), eq("127.0.0.1"));
    }

    @Test
    void register_whenBodyIsEmpty_shouldReturnValidationException() {
        ServerRequest request = emptyBodyRequest("Chrome", "127.0.0.1");

        StepVerifier.create(authHandler.register(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void register_whenEmailAlreadyExists_shouldPropagateConflictException() {
        when(authService.register(any(RegisterRequest.class), anyString(), anyString()))
                .thenReturn(Mono.error(new ConflictException("Email is already registered")));

        ServerRequest request = requestWithBody(
                RegisterRequest.builder().fullName("Test User")
                        .email("existing@test.com").password("Password@123").build(),
                "Chrome", "127.0.0.1", null, null);

        StepVerifier.create(authHandler.register(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo("Email is already registered");
                })
                .verify();
    }

    @Test
    void register_whenInvalidEmail_shouldReturnValidationException() {
        ServerRequest request = requestWithBody(
                RegisterRequest.builder().fullName("Test User")
                        .email("not-an-email").password("Password@123").build(),
                "Chrome", "127.0.0.1", null, null);

        StepVerifier.create(authHandler.register(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void register_whenUserAgentIsNull_shouldPassDefaultDeviceName() {
        AuthResponse response = authResponse();
        when(authService.register(any(RegisterRequest.class), eq("Unknown device"), anyString()))
                .thenReturn(Mono.just(response));

        ServerRequest request = requestWithBody(
                RegisterRequest.builder().fullName("Test User")
                        .email("test@test.com").password("Password@123").build(),
                null, "127.0.0.1", null, null);

        StepVerifier.create(authHandler.register(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();
    }

    @Test
    void register_whenUserAgentExceeds150Chars_shouldTruncate() {
        AuthResponse response = authResponse();
        String longAgent = "A".repeat(200);
        when(authService.register(any(RegisterRequest.class), eq("A".repeat(150)), anyString()))
                .thenReturn(Mono.just(response));

        ServerRequest request = requestWithBody(
                RegisterRequest.builder().fullName("Test User")
                        .email("test@test.com").password("Password@123").build(),
                longAgent, "127.0.0.1", null, null);

        StepVerifier.create(authHandler.register(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();
    }

    @Test
    void register_whenXForwardedForHeader_shouldExtractIpFromHeader() {
        AuthResponse response = authResponse();
        when(authService.register(any(RegisterRequest.class), anyString(), eq("10.0.0.1")))
                .thenReturn(Mono.just(response));

        ServerRequest request = requestWithBody(
                RegisterRequest.builder().fullName("Test User")
                        .email("test@test.com").password("Password@123").build(),
                "Chrome", "127.0.0.1", "10.0.0.1, 10.0.0.2", null);

        StepVerifier.create(authHandler.register(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();

        verify(authService).register(any(RegisterRequest.class), anyString(), eq("10.0.0.1"));
    }

    @Test
    void register_whenXRealIpHeader_shouldUseRealIp() {
        AuthResponse response = authResponse();
        when(authService.register(any(RegisterRequest.class), anyString(), eq("192.168.1.1")))
                .thenReturn(Mono.just(response));

        ServerRequest request = requestWithBody(
                RegisterRequest.builder().fullName("Test User")
                        .email("test@test.com").password("Password@123").build(),
                "Chrome", "127.0.0.1", null, "192.168.1.1");

        StepVerifier.create(authHandler.register(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();
    }

    @Test
    void register_whenForwardedForExceeds100Chars_shouldTruncateIp() {
        AuthResponse response = authResponse();
        String longIp = "1".repeat(120);
        when(authService.register(any(RegisterRequest.class), anyString(), eq("1".repeat(100))))
                .thenReturn(Mono.just(response));

        ServerRequest request = requestWithBody(
                RegisterRequest.builder().fullName("Test User")
                        .email("test@test.com").password("Password@123").build(),
                "Chrome", "127.0.0.1", longIp, null);

        StepVerifier.create(authHandler.register(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();
    }

    @Test
    void register_whenNoHeadersAndNoRemoteAddress_shouldUseDefaultIp() {
        AuthResponse response = authResponse();
        when(authService.register(any(RegisterRequest.class), anyString(), eq("Unknown")))
                .thenReturn(Mono.just(response));

        ServerRequest request = requestWithBodyNoAddress(
                RegisterRequest.builder().fullName("Test User")
                        .email("test@test.com").password("Password@123").build(),
                "Chrome");

        StepVerifier.create(authHandler.register(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();
    }

    // ======================== login ========================

    @Test
    void login_whenValidCredentials_shouldReturn200() {
        AuthResponse response = authResponse();
        when(authService.login(any(LoginRequest.class), eq("Chrome"), eq("127.0.0.1")))
                .thenReturn(Mono.just(response));

        ServerRequest request = requestWithBody(
                LoginRequest.builder().email("test@test.com").password("Password@123").build(),
                "Chrome", "127.0.0.1", null, null);

        StepVerifier.create(authHandler.login(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void login_whenBodyIsEmpty_shouldReturnValidationException() {
        ServerRequest request = emptyBodyRequest("Chrome", "127.0.0.1");

        StepVerifier.create(authHandler.login(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void login_whenInvalidCredentials_shouldPropagateAuthenticationException() {
        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenReturn(Mono.error(new AuthenticationException("Invalid email or password")));

        ServerRequest request = requestWithBody(
                LoginRequest.builder().email("test@test.com").password("wrong").build(),
                "Chrome", "127.0.0.1", null, null);

        StepVerifier.create(authHandler.login(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage()).isEqualTo("Invalid email or password");
                })
                .verify();
    }

    @Test
    void login_whenInvalidEmailFormat_shouldReturnValidationException() {
        ServerRequest request = requestWithBody(
                LoginRequest.builder().email("not-an-email").password("Password@123").build(),
                "Chrome", "127.0.0.1", null, null);

        StepVerifier.create(authHandler.login(request))
                .expectError(ValidationException.class)
                .verify();
    }

    // ======================== refresh ========================

    @Test
    void refresh_whenValidToken_shouldReturn200() {
        AuthResponse response = authResponse();
        when(authService.refresh(any(RefreshTokenRequest.class), eq("Chrome"), eq("127.0.0.1")))
                .thenReturn(Mono.just(response));

        ServerRequest request = requestWithBody(
                RefreshTokenRequest.builder().refreshToken("valid-refresh-token").build(),
                "Chrome", "127.0.0.1", null, null);

        StepVerifier.create(authHandler.refresh(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void refresh_whenBodyIsEmpty_shouldReturnValidationException() {
        ServerRequest request = emptyBodyRequest("Chrome", "127.0.0.1");

        StepVerifier.create(authHandler.refresh(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void refresh_whenTokenIsInvalid_shouldPropagateAuthenticationException() {
        when(authService.refresh(any(RefreshTokenRequest.class), anyString(), anyString()))
                .thenReturn(Mono.error(new AuthenticationException("Invalid refresh token")));

        ServerRequest request = requestWithBody(
                RefreshTokenRequest.builder().refreshToken("invalid-token").build(),
                "Chrome", "127.0.0.1", null, null);

        StepVerifier.create(authHandler.refresh(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage()).isEqualTo("Invalid refresh token");
                })
                .verify();
    }

    @Test
    void refresh_whenBlankRefreshToken_shouldReturnValidationException() {
        ServerRequest request = requestWithBody(
                RefreshTokenRequest.builder().refreshToken("  ").build(),
                "Chrome", "127.0.0.1", null, null);

        StepVerifier.create(authHandler.refresh(request))
                .expectError(ValidationException.class)
                .verify();
    }

    // ======================== logout ========================

    @Test
    void logout_whenAuthenticated_shouldReturn200() {
        MessageResponse response = MessageResponse.builder().message("Logged out successfully").build();
        when(authService.logout(eq(1L), any(RefreshTokenRequest.class)))
                .thenReturn(Mono.just(response));

        ServerRequest request = authenticatedRequestWithBody(
                RefreshTokenRequest.builder().refreshToken("refresh-token").build(), 1L);

        StepVerifier.create(authHandler.logout(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void logout_whenNotAuthenticated_shouldReturnAuthenticationException() {
        ServerRequest request = unauthenticatedRequestWithBody(
                RefreshTokenRequest.builder().refreshToken("refresh-token").build());

        StepVerifier.create(authHandler.logout(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage()).isEqualTo("Authentication is required");
                })
                .verify();
    }

    @Test
    void logout_whenPrincipalIsString_shouldConvertToLong() {
        MessageResponse response = MessageResponse.builder().message("Logged out successfully").build();
        when(authService.logout(eq(1L), any(RefreshTokenRequest.class)))
                .thenReturn(Mono.just(response));

        ServerRequest request = authenticatedRequestWithBodyPrincipalString(
                RefreshTokenRequest.builder().refreshToken("refresh-token").build(), "1");

        StepVerifier.create(authHandler.logout(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void logout_whenPrincipalIsNonNumericString_shouldReturnAuthenticationException() {
        ServerRequest request = authenticatedRequestWithBodyPrincipalString(
                RefreshTokenRequest.builder().refreshToken("refresh-token").build(), "abc");

        StepVerifier.create(authHandler.logout(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage()).isEqualTo("Invalid authenticated user id");
                })
                .verify();
    }

    @Test
    void logout_whenPrincipalIsUnknownType_shouldReturnAuthenticationException() {
        ServerRequest request = authenticatedRequestWithBodyPrincipalObject(
                RefreshTokenRequest.builder().refreshToken("refresh-token").build(),
                Boolean.TRUE);

        StepVerifier.create(authHandler.logout(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage()).isEqualTo("Invalid authenticated principal");
                })
                .verify();
    }

    @Test
    void logout_whenBodyIsEmpty_shouldReturnValidationException() {
        ServerRequest request = authenticatedEmptyBodyRequest(1L);

        StepVerifier.create(authHandler.logout(request))
                .expectError(ValidationException.class)
                .verify();
    }

    // ======================== me ========================

    @Test
    void me_whenAuthenticated_shouldReturn200() {
        UserResponse response = userResponse();
        when(authService.getCurrentUser(1L)).thenReturn(Mono.just(response));

        ServerRequest request = authenticatedRequestNoBody(1L);

        StepVerifier.create(authHandler.me(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void me_whenNotAuthenticated_shouldReturnAuthenticationException() {
        ServerRequest request = unauthenticatedRequestNoBody();

        StepVerifier.create(authHandler.me(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage()).isEqualTo("Authentication is required");
                })
                .verify();
    }

    @Test
    void me_whenUserNotFound_shouldPropagateNotFoundException() {
        when(authService.getCurrentUser(99L))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        ServerRequest request = authenticatedRequestNoBody(99L);

        StepVerifier.create(authHandler.me(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                })
                .verify();
    }

    // ======================== sessions ========================

    @Test
    void sessions_whenAuthenticated_shouldReturn200() {
        SessionResponse first = SessionResponse.builder()
                .id(10L).deviceName("Chrome").ipAddress("127.0.0.1").active(true)
                .createdAt(LocalDateTime.now()).lastAccessedAt(LocalDateTime.now())
                .build();
        when(authService.getSessions(1L)).thenReturn(Flux.just(first));

        ServerRequest request = authenticatedRequestNoBody(1L);

        StepVerifier.create(authHandler.sessions(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void sessions_whenNotAuthenticated_shouldReturnAuthenticationException() {
        ServerRequest request = unauthenticatedRequestNoBody();

        StepVerifier.create(authHandler.sessions(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage()).isEqualTo("Authentication is required");
                })
                .verify();
    }

    // ======================== revokeSession ========================

    @Test
    void revokeSession_whenValidSessionId_shouldReturn200() {
        MessageResponse response = MessageResponse.builder()
                .message("Session revoked successfully").build();
        when(authService.revokeSession(1L, 10L)).thenReturn(Mono.just(response));

        ServerRequest request = authenticatedRequestWithPathVar(1L, "sessionId", "10");

        StepVerifier.create(authHandler.revokeSession(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void revokeSession_whenSessionIdIsZero_shouldReturnValidationException() {
        ServerRequest request = authenticatedRequestWithPathVar(1L, "sessionId", "0");

        StepVerifier.create(authHandler.revokeSession(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(((ValidationException) error).getErrors()).containsExactly("Session id must be positive");
                })
                .verify();
    }

    @Test
    void revokeSession_whenSessionIdIsNegative_shouldReturnValidationException() {
        ServerRequest request = authenticatedRequestWithPathVar(1L, "sessionId", "-5");

        StepVerifier.create(authHandler.revokeSession(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(((ValidationException) error).getErrors()).containsExactly("Session id must be positive");
                })
                .verify();
    }

    @Test
    void revokeSession_whenSessionIdIsNotANumber_shouldReturnValidationException() {
        ServerRequest request = authenticatedRequestWithPathVar(1L, "sessionId", "abc");

        StepVerifier.create(authHandler.revokeSession(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(((ValidationException) error).getErrors()).containsExactly("Invalid session id");
                })
                .verify();
    }

    @Test
    void revokeSession_whenNotAuthenticated_shouldReturnAuthenticationException() {
        ServerRequest request = unauthenticatedRequestWithPathVar("sessionId", "10");

        StepVerifier.create(authHandler.revokeSession(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthenticationException.class);
                    assertThat(error.getMessage()).isEqualTo("Authentication is required");
                })
                .verify();
    }

    @Test
    void revokeSession_whenSessionNotFound_shouldPropagateNotFoundException() {
        when(authService.revokeSession(1L, 99L))
                .thenReturn(Mono.error(new NotFoundException("Session not found")));

        ServerRequest request = authenticatedRequestWithPathVar(1L, "sessionId", "99");

        StepVerifier.create(authHandler.revokeSession(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("Session not found");
                })
                .verify();
    }

    // ======================== mock builders ========================

    @SuppressWarnings("unchecked")
    private ServerRequest requestWithBody(
            Object body, String userAgent, String remoteIp,
            String forwardedFor, String realIp) {

        ServerRequest request = mock(ServerRequest.class);
        when(request.bodyToMono(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type.isInstance(body)) return Mono.just(body);
            return Mono.empty();
        });
        when(request.principal()).thenReturn(Mono.empty());

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(headers.firstHeader("User-Agent")).thenReturn(userAgent);
        when(headers.firstHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        when(headers.firstHeader("X-Real-IP")).thenReturn(realIp);

        if (remoteIp != null) {
            when(request.remoteAddress())
                    .thenReturn(Optional.of(new InetSocketAddress(remoteIp, 8080)));
        } else {
            when(request.remoteAddress()).thenReturn(Optional.empty());
        }

        return request;
    }

    private ServerRequest emptyBodyRequest(String userAgent, String remoteIp) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.bodyToMono(any(Class.class))).thenReturn(Mono.empty());
        when(request.principal()).thenReturn(Mono.empty());

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(headers.firstHeader("User-Agent")).thenReturn(userAgent);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress(remoteIp, 8080)));

        return request;
    }

    private ServerRequest requestWithBodyNoAddress(
            Object body, String userAgent) {

        ServerRequest request = mock(ServerRequest.class);
        when(request.bodyToMono(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type.isInstance(body)) return Mono.just(body);
            return Mono.empty();
        });
        when(request.principal()).thenReturn(Mono.empty());

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(headers.firstHeader("User-Agent")).thenReturn(userAgent);
        when(request.remoteAddress()).thenReturn(Optional.empty());

        return request;
    }

    @SuppressWarnings("unchecked")
    private ServerRequest authenticatedRequestWithBody(Object body, Long userId) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.bodyToMono(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type.isInstance(body)) return Mono.just(body);
            return Mono.empty();
        });
        when(request.principal())
                .thenReturn((Mono) Mono.just(new UsernamePasswordAuthenticationToken(userId, null)));

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1", 8080)));

        return request;
    }

    @SuppressWarnings("unchecked")
    private ServerRequest unauthenticatedRequestWithBody(Object body) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.bodyToMono(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type.isInstance(body)) return Mono.just(body);
            return Mono.empty();
        });
        when(request.principal()).thenReturn(Mono.empty());

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1", 8080)));

        return request;
    }

    @SuppressWarnings("unchecked")
    private ServerRequest authenticatedRequestWithBodyPrincipalString(
            Object body, String principalValue) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.bodyToMono(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type.isInstance(body)) return Mono.just(body);
            return Mono.empty();
        });
        when(request.principal())
                .thenReturn((Mono) Mono.just(new UsernamePasswordAuthenticationToken(principalValue, null)));

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1", 8080)));

        return request;
    }

    @SuppressWarnings("unchecked")
    private ServerRequest authenticatedRequestWithBodyPrincipalObject(
            Object body, Object principalValue) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.bodyToMono(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type.isInstance(body)) return Mono.just(body);
            return Mono.empty();
        });
        when(request.principal())
                .thenReturn((Mono) Mono.just(new UsernamePasswordAuthenticationToken(principalValue, null)));

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1", 8080)));

        return request;
    }

    private ServerRequest authenticatedRequestNoBody(Long userId) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.principal())
                .thenReturn((Mono) Mono.just(new UsernamePasswordAuthenticationToken(userId, null)));

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1", 8080)));

        return request;
    }

    private ServerRequest unauthenticatedRequestNoBody() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.principal()).thenReturn(Mono.empty());

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1", 8080)));

        return request;
    }

    private ServerRequest authenticatedRequestWithPathVar(
            Long userId, String varName, String varValue) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.principal())
                .thenReturn((Mono) Mono.just(new UsernamePasswordAuthenticationToken(userId, null)));
        when(request.pathVariable(varName)).thenReturn(varValue);

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1", 8080)));

        return request;
    }

    private ServerRequest unauthenticatedRequestWithPathVar(
            String varName, String varValue) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.principal()).thenReturn(Mono.empty());
        when(request.pathVariable(varName)).thenReturn(varValue);

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1", 8080)));

        return request;
    }

    private ServerRequest authenticatedEmptyBodyRequest(Long userId) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.bodyToMono(any(Class.class))).thenReturn(Mono.empty());
        when(request.principal())
                .thenReturn((Mono) Mono.just(new UsernamePasswordAuthenticationToken(userId, null)));

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1", 8080)));

        return request;
    }

    private AuthResponse authResponse() {
        return AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(userResponse())
                .build();
    }

    private UserResponse userResponse() {
        return UserResponse.builder()
                .id(1L).fullName("Test User").email("test@test.com")
                .role("ROLE_CUSTOMER").enabled(true)
                .build();
    }
}
