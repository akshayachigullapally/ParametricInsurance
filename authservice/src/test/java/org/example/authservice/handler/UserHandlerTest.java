package org.example.authservice.handler;

import jakarta.validation.Validator;
import org.example.authservice.dto.*;
import org.example.authservice.exception.ForbiddenException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.exception.ValidationException;
import org.example.authservice.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserHandlerTest {

    @Mock
    private UserProfileService userProfileService;

    private Validator validator;
    private UserHandler userHandler;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.afterPropertiesSet();
        validator = factory;
        userHandler = new UserHandler(userProfileService, validator);
    }

    // ======================== saveMyProfile ========================

    @Test
    void saveMyProfile_whenAuthenticated_shouldReturn200() {
        UserResponse response = userResponse(1L);
        when(userProfileService.saveProfile(eq(1L), any(UpdateProfileRequest.class)))
                .thenReturn(Mono.just(response));

        ServerRequest request = authenticatedRequestWithBody(
                validUpdateProfileRequest(), 1L);

        StepVerifier.create(userHandler.saveMyProfile(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void saveMyProfile_whenNotAuthenticated_shouldReturnForbiddenException() {
        ServerRequest request = unauthenticatedRequestWithBody(validUpdateProfileRequest());

        StepVerifier.create(userHandler.saveMyProfile(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ForbiddenException.class);
                    assertThat(error.getMessage()).isEqualTo("Authenticated user is required");
                })
                .verify();
    }

    @Test
    void saveMyProfile_whenBodyIsEmpty_shouldReturnValidationException() {
        ServerRequest request = authenticatedEmptyBodyRequest(1L);

        StepVerifier.create(userHandler.saveMyProfile(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void saveMyProfile_whenUserNotFound_shouldPropagateNotFoundException() {
        when(userProfileService.saveProfile(eq(99L), any(UpdateProfileRequest.class)))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        ServerRequest request = authenticatedRequestWithBody(
                validUpdateProfileRequest(), 99L);

        StepVerifier.create(userHandler.saveMyProfile(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                })
                .verify();
    }

    // ======================== getMyProfile ========================

    @Test
    void getMyProfile_whenAuthenticated_shouldReturn200() {
        UserResponse response = userResponse(1L);
        when(userProfileService.getMyProfile(1L)).thenReturn(Mono.just(response));

        ServerRequest request = authenticatedRequestNoBody(1L);

        StepVerifier.create(userHandler.getMyProfile(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getMyProfile_whenNotAuthenticated_shouldReturnForbiddenException() {
        ServerRequest request = unauthenticatedRequestNoBody();

        StepVerifier.create(userHandler.getMyProfile(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ForbiddenException.class);
                    assertThat(error.getMessage()).isEqualTo("Authenticated user is required");
                })
                .verify();
    }

    @Test
    void getMyProfile_whenUserNotFound_shouldPropagateNotFoundException() {
        when(userProfileService.getMyProfile(99L))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        ServerRequest request = authenticatedRequestNoBody(99L);

        StepVerifier.create(userHandler.getMyProfile(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                })
                .verify();
    }

    // ======================== deactivateMyProfile ========================

    @Test
    void deactivateMyProfile_whenAuthenticated_shouldReturn200() {
        MessageResponse response = MessageResponse.builder()
                .message("Profile deactivated successfully").build();
        when(userProfileService.deactivateProfile(1L)).thenReturn(Mono.just(response));

        ServerRequest request = authenticatedRequestNoBody(1L);

        StepVerifier.create(userHandler.deactivateMyProfile(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void deactivateMyProfile_whenNotAuthenticated_shouldReturnForbiddenException() {
        ServerRequest request = unauthenticatedRequestNoBody();

        StepVerifier.create(userHandler.deactivateMyProfile(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ForbiddenException.class);
                    assertThat(error.getMessage()).isEqualTo("Authenticated user is required");
                })
                .verify();
    }

    @Test
    void deactivateMyProfile_whenProfileNotFound_shouldPropagateNotFoundException() {
        when(userProfileService.deactivateProfile(99L))
                .thenReturn(Mono.error(new NotFoundException("User profile not found")));

        ServerRequest request = authenticatedRequestNoBody(99L);

        StepVerifier.create(userHandler.deactivateMyProfile(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User profile not found");
                })
                .verify();
    }

    // ======================== getAllUsers ========================

    @Test
    void getAllUsers_whenNoFilters_shouldReturn200() {
        UserResponse response = userResponse(1L);
        when(userProfileService.getAllUsers(null, null))
                .thenReturn(Flux.just(response));

        ServerRequest request = requestWithNoQueryParams();

        StepVerifier.create(userHandler.getAllUsers(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getAllUsers_whenStatusFilterProvided_shouldPassToService() {
        UserResponse response = userResponse(1L);
        when(userProfileService.getAllUsers("ACTIVE", null))
                .thenReturn(Flux.just(response));

        ServerRequest request = requestWithQueryParam("status", "ACTIVE");

        StepVerifier.create(userHandler.getAllUsers(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(userProfileService).getAllUsers("ACTIVE", null);
    }

    @Test
    void getAllUsers_whenSegmentFilterProvided_shouldPassToService() {
        UserResponse response = userResponse(1L);
        when(userProfileService.getAllUsers(null, "INDIVIDUAL"))
                .thenReturn(Flux.just(response));

        ServerRequest request = requestWithQueryParam("segment", "INDIVIDUAL");

        StepVerifier.create(userHandler.getAllUsers(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(userProfileService).getAllUsers(null, "INDIVIDUAL");
    }

    @Test
    void getAllUsers_whenBothFiltersProvided_shouldPassToService() {
        UserResponse response = userResponse(1L);
        when(userProfileService.getAllUsers("ACTIVE", "FARMER"))
                .thenReturn(Flux.just(response));

        ServerRequest request = requestWithTwoQueryParams("status", "ACTIVE", "segment", "FARMER");

        StepVerifier.create(userHandler.getAllUsers(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(userProfileService).getAllUsers("ACTIVE", "FARMER");
    }

    @Test
    void getAllUsers_whenNoUsersFound_shouldReturnEmptyFlux() {
        when(userProfileService.getAllUsers(null, null))
                .thenReturn(Flux.empty());

        ServerRequest request = requestWithNoQueryParams();

        StepVerifier.create(userHandler.getAllUsers(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    // ======================== getUser ========================

    @Test
    void getUser_whenValidUserId_shouldReturn200() {
        UserResponse response = userResponse(5L);
        when(userProfileService.getUser(5L)).thenReturn(Mono.just(response));

        ServerRequest request = requestWithPathVar("userId", "5");

        StepVerifier.create(userHandler.getUser(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void getUser_whenUserIdIsZero_shouldReturnValidationException() {
        ServerRequest request = requestWithPathVar("userId", "0");

        StepVerifier.create(userHandler.getUser(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo("User id must be positive");
                })
                .verify();
    }

    @Test
    void getUser_whenUserIdIsNegative_shouldReturnValidationException() {
        ServerRequest request = requestWithPathVar("userId", "-1");

        StepVerifier.create(userHandler.getUser(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo("User id must be positive");
                })
                .verify();
    }

    @Test
    void getUser_whenUserIdIsNotANumber_shouldReturnValidationException() {
        ServerRequest request = requestWithPathVar("userId", "abc");

        StepVerifier.create(userHandler.getUser(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo("Invalid user id");
                })
                .verify();
    }

    @Test
    void getUser_whenUserNotFound_shouldPropagateNotFoundException() {
        when(userProfileService.getUser(99L))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        ServerRequest request = requestWithPathVar("userId", "99");

        StepVerifier.create(userHandler.getUser(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                })
                .verify();
    }

    // ======================== updateStatus ========================

    @Test
    void updateStatus_whenValidRequest_shouldReturn200() {
        UserResponse response = userResponse(1L);
        when(userProfileService.updateStatus(eq(1L), any(UpdateUserStatusRequest.class)))
                .thenReturn(Mono.just(response));

        ServerRequest request = authenticatedRequestWithPathVarAndBody(
                1L, "userId", "1",
                UpdateUserStatusRequest.builder().status("SUSPENDED").build());

        StepVerifier.create(userHandler.updateStatus(request))
                .assertNext(ex -> assertThat(ex.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void updateStatus_whenUserIdIsZero_shouldReturnValidationException() {
        ServerRequest request = requestWithPathVarAndBody(
                "userId", "0",
                UpdateUserStatusRequest.builder().status("ACTIVE").build());

        StepVerifier.create(userHandler.updateStatus(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo("User id must be positive");
                })
                .verify();
    }

    @Test
    void updateStatus_whenUserIdIsNotANumber_shouldReturnValidationException() {
        ServerRequest request = requestWithPathVarAndBody(
                "userId", "abc",
                UpdateUserStatusRequest.builder().status("ACTIVE").build());

        StepVerifier.create(userHandler.updateStatus(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo("Invalid user id");
                })
                .verify();
    }

    @Test
    void updateStatus_whenBodyIsEmpty_shouldReturnValidationException() {
        ServerRequest request = requestWithPathVarEmptyBody("userId", "1");

        StepVerifier.create(userHandler.updateStatus(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void updateStatus_whenInvalidStatus_shouldPropagateValidationException() {
        when(userProfileService.updateStatus(eq(1L), any(UpdateUserStatusRequest.class)))
                .thenReturn(Mono.error(
                        new ValidationException("Status must be ACTIVE, SUSPENDED or INACTIVE")));

        ServerRequest request = authenticatedRequestWithPathVarAndBody(
                1L, "userId", "1",
                UpdateUserStatusRequest.builder().status("DELETED").build());

        StepVerifier.create(userHandler.updateStatus(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Status must be ACTIVE, SUSPENDED or INACTIVE");
                })
                .verify();
    }

    @Test
    void updateStatus_whenUserNotFound_shouldPropagateNotFoundException() {
        when(userProfileService.updateStatus(eq(99L), any(UpdateUserStatusRequest.class)))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        ServerRequest request = authenticatedRequestWithPathVarAndBody(
                99L, "userId", "99",
                UpdateUserStatusRequest.builder().status("ACTIVE").build());

        StepVerifier.create(userHandler.updateStatus(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                })
                .verify();
    }

    // ======================== mock builders ========================

    @SuppressWarnings("unchecked")
    private ServerRequest requestWithNoQueryParams() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.queryParam("status")).thenReturn(Optional.empty());
        when(request.queryParam("segment")).thenReturn(Optional.empty());
        when(request.headers()).thenReturn(mock(ServerRequest.Headers.class));
        return request;
    }

    private ServerRequest requestWithQueryParam(String key, String value) {
        ServerRequest request = mock(ServerRequest.class);
        if ("status".equals(key)) {
            when(request.queryParam("status")).thenReturn(Optional.of(value));
            when(request.queryParam("segment")).thenReturn(Optional.empty());
        } else {
            when(request.queryParam("status")).thenReturn(Optional.empty());
            when(request.queryParam("segment")).thenReturn(Optional.of(value));
        }
        when(request.headers()).thenReturn(mock(ServerRequest.Headers.class));
        return request;
    }

    private ServerRequest requestWithTwoQueryParams(
            String key1, String value1, String key2, String value2) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.queryParam("status")).thenReturn(Optional.of(value1));
        when(request.queryParam("segment")).thenReturn(Optional.of(value2));
        when(request.headers()).thenReturn(mock(ServerRequest.Headers.class));
        return request;
    }

    private ServerRequest requestWithPathVar(String varName, String varValue) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.pathVariable(varName)).thenReturn(varValue);
        when(request.headers()).thenReturn(mock(ServerRequest.Headers.class));
        return request;
    }

    @SuppressWarnings("unchecked")
    private ServerRequest requestWithPathVarAndBody(
            String varName, String varValue, Object body) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.pathVariable(varName)).thenReturn(varValue);
        when(request.bodyToMono(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type.isInstance(body)) return Mono.just(body);
            return Mono.empty();
        });
        when(request.headers()).thenReturn(mock(ServerRequest.Headers.class));
        return request;
    }

    private ServerRequest requestWithPathVarEmptyBody(String varName, String varValue) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.pathVariable(varName)).thenReturn(varValue);
        when(request.bodyToMono(any(Class.class))).thenReturn(Mono.empty());
        when(request.headers()).thenReturn(mock(ServerRequest.Headers.class));
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

    @SuppressWarnings("unchecked")
    private ServerRequest authenticatedRequestWithPathVarAndBody(
            Long userId, String varName, String varValue, Object body) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.principal())
                .thenReturn((Mono) Mono.just(new UsernamePasswordAuthenticationToken(userId, null)));
        when(request.pathVariable(varName)).thenReturn(varValue);
        when(request.bodyToMono(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type.isInstance(body)) return Mono.just(body);
            return Mono.empty();
        });

        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(request.remoteAddress())
                .thenReturn(Optional.of(new InetSocketAddress("127.0.0.1", 8080)));

        return request;
    }

    // ======================== test data builders ========================

    private UpdateProfileRequest validUpdateProfileRequest() {
        AddressRequest addressRequest = AddressRequest.builder()
                .addressLineOne("1 Main Road")
                .city("Hyderabad")
                .state("Telangana")
                .postalCode("500001")
                .country("India")
                .build();
        return UpdateProfileRequest.builder()
                .fullName("Updated User")
                .phoneNumber("9000000000")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .customerSegment("INDIVIDUAL")
                .address(addressRequest)
                .build();
    }

    private UserResponse userResponse(Long id) {
        return UserResponse.builder()
                .id(id)
                .fullName("Test User")
                .email("test@test.com")
                .role("ROLE_CUSTOMER")
                .enabled(true)
                .emailVerified(false)
                .phoneNumber("9000000000")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .customerSegment("INDIVIDUAL")
                .profileStatus("ACTIVE")
                .addresses(List.of())
                .createdAt(LocalDateTime.now())
                .build();
    }
}
