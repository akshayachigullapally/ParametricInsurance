package org.example.authservice.service;

import org.example.authservice.dto.AddressRequest;
import org.example.authservice.dto.UpdateProfileRequest;
import org.example.authservice.dto.UpdateUserStatusRequest;
import org.example.authservice.exception.ForbiddenException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.exception.ValidationException;
import org.example.authservice.model.Address;
import org.example.authservice.model.UserCredential;
import org.example.authservice.model.UserProfile;
import org.example.authservice.repository.AddressRepository;
import org.example.authservice.repository.UserCredentialRepository;
import org.example.authservice.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserCredentialRepository userRepository;

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService=new UserProfileService(
                userRepository,
                profileRepository,
                addressRepository,
                notificationServiceClient
        );
        lenient().when(notificationServiceClient.audit(any(),any(),any(),any(),any(),any()))
                .thenReturn(reactor.core.publisher.Mono.empty());
    }

    @Test
    void saveProfile_whenCustomerAndProfileExist_shouldUpdateUserProfileAndAddress() {
        UserCredential user=user(1L,"ROLE_CUSTOMER");
        UserProfile profile=profile(10L,1L,"ACTIVE","INDIVIDUAL");
        Address existingAddress=address(100L,10L);
        UpdateProfileRequest request=profileRequest();

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(profileRepository.findByAuthUserId(1L)).thenReturn(Mono.just(profile));
        when(profileRepository.save(profile)).thenReturn(Mono.just(profile));
        when(addressRepository.findByUserProfileIdAndPrimaryAddressTrue(10L))
                .thenReturn(Mono.just(existingAddress));
        when(addressRepository.save(existingAddress))
                .thenReturn(Mono.just(existingAddress));
        when(addressRepository.findAllByUserProfileId(10L))
                .thenReturn(Flux.just(existingAddress));

        StepVerifier.create(userProfileService.saveProfile(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getFullName()).isEqualTo("Updated User");
                    assertThat(response.getPhoneNumber()).isEqualTo("9000000000");
                    assertThat(response.getCustomerSegment()).isEqualTo("SMALL_BUSINESS");
                    assertThat(response.getProfileStatus()).isEqualTo("ACTIVE");
                    assertThat(response.getAddresses()).hasSize(1);
                    assertThat(response.getAddresses().get(0).getCity())
                            .isEqualTo("Hyderabad");
                })
                .verifyComplete();

        assertThat(user.getFullName()).isEqualTo("Updated User");
        assertThat(user.getUpdatedAt()).isNotNull();

        assertThat(profile.getPhoneNumber()).isEqualTo("9000000000");
        assertThat(profile.getDateOfBirth()).isEqualTo(LocalDate.of(2000,1,1));
        assertThat(profile.getCustomerSegment()).isEqualTo("SMALL_BUSINESS");
        assertThat(profile.getStatus()).isEqualTo("ACTIVE");
        assertThat(profile.getUpdatedAt()).isNotNull();

        assertThat(existingAddress.getAddressLineOne()).isEqualTo("1 Main Road");
        assertThat(existingAddress.getAddressLineTwo()).isNull();
        assertThat(existingAddress.getCity()).isEqualTo("Hyderabad");
        assertThat(existingAddress.getState()).isEqualTo("Telangana");
        assertThat(existingAddress.getPostalCode()).isEqualTo("500001");
        assertThat(existingAddress.getCountry()).isEqualTo("India");
        assertThat(existingAddress.getPrimaryAddress()).isTrue();

        verify(userRepository).findById(1L);
        verify(userRepository).save(user);
        verify(profileRepository).findByAuthUserId(1L);
        verify(profileRepository).save(profile);
        verify(addressRepository)
                .findByUserProfileIdAndPrimaryAddressTrue(10L);
        verify(addressRepository).save(existingAddress);
        verify(addressRepository).findAllByUserProfileId(10L);
    }

    @Test
    void saveProfile_whenProfileDoesNotExist_shouldCreateProfileAndAddress() {
        UserCredential user=user(1L,"ROLE_CUSTOMER");
        UpdateProfileRequest request=profileRequest();

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(profileRepository.findByAuthUserId(1L)).thenReturn(Mono.empty());

        when(profileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation->{
                    UserProfile savedProfile=invocation.getArgument(0);
                    savedProfile.setId(10L);
                    return Mono.just(savedProfile);
                });

        when(addressRepository.findByUserProfileIdAndPrimaryAddressTrue(10L))
                .thenReturn(Mono.empty());

        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation->{
                    Address savedAddress=invocation.getArgument(0);
                    savedAddress.setId(100L);
                    return Mono.just(savedAddress);
                });

        when(addressRepository.findAllByUserProfileId(10L))
                .thenAnswer(invocation->Flux.just(address(100L,10L)));

        StepVerifier.create(userProfileService.saveProfile(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getFullName()).isEqualTo("Updated User");
                    assertThat(response.getPhoneNumber()).isEqualTo("9000000000");
                    assertThat(response.getProfileStatus()).isEqualTo("ACTIVE");
                    assertThat(response.getCustomerSegment()).isEqualTo("SMALL_BUSINESS");
                    assertThat(response.getAddresses()).hasSize(1);
                })
                .verifyComplete();

        ArgumentCaptor<UserProfile> profileCaptor=
                ArgumentCaptor.forClass(UserProfile.class);

        verify(profileRepository).save(profileCaptor.capture());

        UserProfile capturedProfile=profileCaptor.getValue();

        assertThat(capturedProfile.getId()).isEqualTo(10L);
        assertThat(capturedProfile.getAuthUserId()).isEqualTo(1L);
        assertThat(capturedProfile.getPhoneNumber()).isEqualTo("9000000000");
        assertThat(capturedProfile.getDateOfBirth())
                .isEqualTo(LocalDate.of(2000,1,1));
        assertThat(capturedProfile.getCustomerSegment())
                .isEqualTo("SMALL_BUSINESS");
        assertThat(capturedProfile.getStatus()).isEqualTo("ACTIVE");
        assertThat(capturedProfile.getCreatedAt()).isNotNull();
        assertThat(capturedProfile.getUpdatedAt()).isNotNull();

        ArgumentCaptor<Address> addressCaptor=
                ArgumentCaptor.forClass(Address.class);

        verify(addressRepository).save(addressCaptor.capture());

        Address capturedAddress=addressCaptor.getValue();

        assertThat(capturedAddress.getId()).isEqualTo(100L);
        assertThat(capturedAddress.getUserProfileId()).isEqualTo(10L);
        assertThat(capturedAddress.getAddressLineOne()).isEqualTo("1 Main Road");
        assertThat(capturedAddress.getAddressLineTwo()).isNull();
        assertThat(capturedAddress.getCity()).isEqualTo("Hyderabad");
        assertThat(capturedAddress.getState()).isEqualTo("Telangana");
        assertThat(capturedAddress.getPostalCode()).isEqualTo("500001");
        assertThat(capturedAddress.getCountry()).isEqualTo("India");
        assertThat(capturedAddress.getPrimaryAddress()).isTrue();
        assertThat(capturedAddress.getCreatedAt()).isNotNull();
        assertThat(capturedAddress.getUpdatedAt()).isNotNull();
    }

    @Test
    void saveProfile_whenUserDoesNotExist_shouldReturnNotFoundException() {
        UpdateProfileRequest request=mock(UpdateProfileRequest.class);

        when(userRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(userProfileService.saveProfile(99L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                })
                .verify();

        verify(userRepository).findById(99L);
        verify(userRepository,never()).save(any());
        verifyNoInteractions(profileRepository,addressRepository);
    }

    @Test
    void saveProfile_whenUserIsNotCustomer_shouldReturnForbiddenException() {
        UserCredential admin=user(1L,"ROLE_ADMIN");
        UpdateProfileRequest request=mock(UpdateProfileRequest.class);

        when(userRepository.findById(1L)).thenReturn(Mono.just(admin));

        StepVerifier.create(userProfileService.saveProfile(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ForbiddenException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Profile management is available only for customers");
                })
                .verify();

        verify(userRepository).findById(1L);
        verify(userRepository,never()).save(any());
        verifyNoInteractions(profileRepository,addressRepository);
    }


    @Test
    void getMyProfile_whenUserProfileAndAddressesExist_shouldReturnCompleteResponse() {
        UserCredential user=user(1L,"ROLE_CUSTOMER");
        UserProfile profile=profile(10L,1L,"ACTIVE","INDIVIDUAL");
        Address address=address(100L,10L);

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(profileRepository.findByAuthUserId(1L)).thenReturn(Mono.just(profile));
        when(addressRepository.findAllByUserProfileId(10L))
                .thenReturn(Flux.just(address));

        StepVerifier.create(userProfileService.getMyProfile(1L))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getFullName()).isEqualTo("Test User");
                    assertThat(response.getEmail()).isEqualTo("user@test.com");
                    assertThat(response.getRole()).isEqualTo("ROLE_CUSTOMER");
                    assertThat(response.getEnabled()).isTrue();
                    assertThat(response.getPhoneNumber()).isEqualTo("9000000000");
                    assertThat(response.getDateOfBirth())
                            .isEqualTo(LocalDate.of(2000,1,1));
                    assertThat(response.getCustomerSegment()).isEqualTo("INDIVIDUAL");
                    assertThat(response.getProfileStatus()).isEqualTo("ACTIVE");
                    assertThat(response.getAddresses()).hasSize(1);
                    assertThat(response.getAddresses().get(0).getPostalCode())
                            .isEqualTo("500001");
                })
                .verifyComplete();

        verify(userRepository).findById(1L);
        verify(profileRepository).findByAuthUserId(1L);
        verify(addressRepository).findAllByUserProfileId(10L);
    }

    @Test
    void getMyProfile_whenUserDoesNotExist_shouldReturnNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(userProfileService.getMyProfile(99L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                })
                .verify();

        verify(userRepository).findById(99L);
        verifyNoInteractions(profileRepository,addressRepository);
    }

    @Test
    void getMyProfile_whenProfileDoesNotExist_shouldReturnNotFoundException() {
        UserCredential user=user(1L,"ROLE_CUSTOMER");

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(profileRepository.findByAuthUserId(1L)).thenReturn(Mono.empty());

        StepVerifier.create(userProfileService.getMyProfile(1L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("User profile is not completed");
                })
                .verify();

        verify(userRepository).findById(1L);
        verify(profileRepository).findByAuthUserId(1L);
        verifyNoInteractions(addressRepository);
    }

    @Test
    void deactivateProfile_whenProfileExists_shouldChangeStatusToInactive() {
        UserProfile profile=profile(10L,1L,"ACTIVE","INDIVIDUAL");
        LocalDateTime oldUpdatedAt=profile.getUpdatedAt();

        when(profileRepository.findByAuthUserId(1L)).thenReturn(Mono.just(profile));
        when(profileRepository.save(profile)).thenReturn(Mono.just(profile));

        StepVerifier.create(userProfileService.deactivateProfile(1L))
                .assertNext(response->assertThat(response.getMessage())
                        .isEqualTo("Profile deactivated successfully"))
                .verifyComplete();

        assertThat(profile.getStatus()).isEqualTo("INACTIVE");
        assertThat(profile.getUpdatedAt()).isAfterOrEqualTo(oldUpdatedAt);

        verify(profileRepository).findByAuthUserId(1L);
        verify(profileRepository).save(profile);
    }

    @Test
    void deactivateProfile_whenProfileDoesNotExist_shouldReturnNotFoundException() {
        when(profileRepository.findByAuthUserId(99L)).thenReturn(Mono.empty());

        StepVerifier.create(userProfileService.deactivateProfile(99L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("User profile not found");
                })
                .verify();

        verify(profileRepository).findByAuthUserId(99L);
        verify(profileRepository,never()).save(any());
    }

    @Test
    void getAllUsers_whenStatusAndSegmentProvided_shouldUseCombinedRepositoryQuery() {
        UserProfile profile=profile(10L,1L,"ACTIVE","INDIVIDUAL");
        UserCredential user=user(1L,"ROLE_CUSTOMER");

        when(profileRepository.findAllByStatusAndCustomerSegment(
                "ACTIVE",
                "INDIVIDUAL"
        )).thenReturn(Flux.just(profile));

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(addressRepository.findAllByUserProfileId(10L))
                .thenReturn(Flux.just(address(100L,10L)));

        StepVerifier.create(userProfileService.getAllUsers(
                        " active ",
                        " individual "
                ))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getProfileStatus()).isEqualTo("ACTIVE");
                    assertThat(response.getCustomerSegment())
                            .isEqualTo("INDIVIDUAL");
                    assertThat(response.getAddresses()).hasSize(1);
                })
                .verifyComplete();

        verify(profileRepository)
                .findAllByStatusAndCustomerSegment("ACTIVE","INDIVIDUAL");
        verify(profileRepository,never()).findAll();
        verify(profileRepository,never()).findAllByStatus(anyString());
        verify(profileRepository,never())
                .findAllByCustomerSegment(anyString());
    }

    @Test
    void getAllUsers_whenOnlyStatusProvided_shouldUseStatusRepositoryQuery() {
        UserProfile profile=profile(10L,1L,"ACTIVE","INDIVIDUAL");
        UserCredential user=user(1L,"ROLE_CUSTOMER");

        when(profileRepository.findAllByStatus("ACTIVE"))
                .thenReturn(Flux.just(profile));
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(addressRepository.findAllByUserProfileId(10L))
                .thenReturn(Flux.empty());

        StepVerifier.create(userProfileService.getAllUsers(" active ",null))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getProfileStatus()).isEqualTo("ACTIVE");
                    assertThat(response.getAddresses()).isEmpty();
                })
                .verifyComplete();

        verify(profileRepository).findAllByStatus("ACTIVE");
        verify(profileRepository,never()).findAll();
        verify(profileRepository,never())
                .findAllByCustomerSegment(anyString());
        verify(profileRepository,never())
                .findAllByStatusAndCustomerSegment(anyString(),anyString());
    }

    @Test
    void getAllUsers_whenOnlySegmentProvided_shouldUseSegmentRepositoryQuery() {
        UserProfile profile=profile(10L,1L,"ACTIVE","FARMER");
        UserCredential user=user(1L,"ROLE_CUSTOMER");

        when(profileRepository.findAllByCustomerSegment("FARMER"))
                .thenReturn(Flux.just(profile));
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(addressRepository.findAllByUserProfileId(10L))
                .thenReturn(Flux.empty());

        StepVerifier.create(userProfileService.getAllUsers(null," farmer "))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getCustomerSegment()).isEqualTo("FARMER");
                })
                .verifyComplete();

        verify(profileRepository).findAllByCustomerSegment("FARMER");
        verify(profileRepository,never()).findAll();
        verify(profileRepository,never()).findAllByStatus(anyString());
        verify(profileRepository,never())
                .findAllByStatusAndCustomerSegment(anyString(),anyString());
    }

    @Test
    void getAllUsers_whenFiltersAreMissing_shouldReturnAllProfiles() {
        UserProfile firstProfile=profile(
                10L,
                1L,
                "ACTIVE",
                "INDIVIDUAL"
        );

        UserProfile secondProfile=profile(
                20L,
                2L,
                "INACTIVE",
                "ENTERPRISE"
        );

        UserCredential firstUser=user(1L,"ROLE_CUSTOMER");

        UserCredential secondUser=UserCredential.builder()
                .id(2L)
                .fullName("Second User")
                .email("second@test.com")
                .password("encoded-password")
                .role("ROLE_CUSTOMER")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(profileRepository.findAll())
                .thenReturn(Flux.just(firstProfile,secondProfile));
        when(userRepository.findById(1L)).thenReturn(Mono.just(firstUser));
        when(userRepository.findById(2L)).thenReturn(Mono.just(secondUser));
        when(addressRepository.findAllByUserProfileId(10L))
                .thenReturn(Flux.empty());
        when(addressRepository.findAllByUserProfileId(20L))
                .thenReturn(Flux.empty());

        StepVerifier.create(userProfileService.getAllUsers(null," "))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getFullName()).isEqualTo("Test User");
                })
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(2L);
                    assertThat(response.getFullName()).isEqualTo("Second User");
                })
                .verifyComplete();

        verify(profileRepository).findAll();
        verify(profileRepository,never()).findAllByStatus(anyString());
        verify(profileRepository,never())
                .findAllByCustomerSegment(anyString());
        verify(profileRepository,never())
                .findAllByStatusAndCustomerSegment(anyString(),anyString());
    }

    @Test
    void getAllUsers_whenProfileUserDoesNotExist_shouldSkipThatProfile() {
        UserProfile firstProfile=profile(
                10L,
                1L,
                "ACTIVE",
                "INDIVIDUAL"
        );

        UserProfile secondProfile=profile(
                20L,
                2L,
                "ACTIVE",
                "FARMER"
        );

        when(profileRepository.findAll())
                .thenReturn(Flux.just(firstProfile,secondProfile));
        when(userRepository.findById(1L)).thenReturn(Mono.just(
                user(1L,"ROLE_CUSTOMER")
        ));
        when(userRepository.findById(2L)).thenReturn(Mono.empty());
        when(addressRepository.findAllByUserProfileId(10L))
                .thenReturn(Flux.empty());

        StepVerifier.create(userProfileService.getAllUsers(null,null))
                .assertNext(response->assertThat(response.getId()).isEqualTo(1L))
                .verifyComplete();

        verify(userRepository).findById(1L);
        verify(userRepository).findById(2L);
        verify(addressRepository).findAllByUserProfileId(10L);
        verify(addressRepository,never()).findAllByUserProfileId(20L);
    }

    @Test
    void getAllUsers_whenStatusIsInvalid_shouldReturnValidationException() {
        StepVerifier.create(Flux.defer(()->
                        userProfileService.getAllUsers("DELETED",null)
                ))
                .expectErrorSatisfies(error->
                        assertThat(error).isInstanceOf(ValidationException.class)
                )
                .verify();

        verifyNoInteractions(
                profileRepository,
                userRepository,
                addressRepository
        );
    }

    @Test
    void getAllUsers_whenSegmentIsInvalid_shouldReturnValidationException() {
        StepVerifier.create(Flux.defer(()->
                        userProfileService.getAllUsers(null,"INVALID")
                ))
                .expectErrorSatisfies(error->
                        assertThat(error).isInstanceOf(ValidationException.class)
                )
                .verify();

        verifyNoInteractions(
                profileRepository,
                userRepository,
                addressRepository
        );
    }

    @Test
    void getUser_whenUserAndProfileExist_shouldReturnUserResponse() {
        UserCredential user=user(1L,"ROLE_CUSTOMER");
        UserProfile profile=profile(10L,1L,"ACTIVE","INDIVIDUAL");
        Address address=address(100L,10L);

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(profileRepository.findByAuthUserId(1L))
                .thenReturn(Mono.just(profile));
        when(addressRepository.findAllByUserProfileId(10L))
                .thenReturn(Flux.just(address));

        StepVerifier.create(userProfileService.getUser(1L))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getFullName()).isEqualTo("Test User");
                    assertThat(response.getProfileStatus()).isEqualTo("ACTIVE");
                    assertThat(response.getCustomerSegment())
                            .isEqualTo("INDIVIDUAL");
                    assertThat(response.getAddresses()).hasSize(1);
                    assertThat(response.getAddresses().get(0).getCity())
                            .isEqualTo("Hyderabad");
                })
                .verifyComplete();

        verify(userRepository).findById(1L);
        verify(profileRepository).findByAuthUserId(1L);
        verify(addressRepository).findAllByUserProfileId(10L);
    }

    @Test
    void getUser_whenUserDoesNotExist_shouldReturnNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(userProfileService.getUser(99L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                })
                .verify();

        verify(userRepository).findById(99L);
        verifyNoInteractions(profileRepository,addressRepository);
    }

    @Test
    void getUser_whenProfileDoesNotExist_shouldReturnNotFoundException() {
        UserCredential user=user(1L,"ROLE_CUSTOMER");

        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(profileRepository.findByAuthUserId(1L)).thenReturn(Mono.empty());

        StepVerifier.create(userProfileService.getUser(1L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("User profile not found");
                })
                .verify();

        verify(userRepository).findById(1L);
        verify(profileRepository).findByAuthUserId(1L);
        verifyNoInteractions(addressRepository);
    }

    @Test
    void updateStatus_whenStatusIsValid_shouldUpdateAndReturnUserResponse() {
        UpdateUserStatusRequest request=mock(UpdateUserStatusRequest.class);
        UserCredential user=user(1L,"ROLE_CUSTOMER");
        UserProfile profile=profile(10L,1L,"ACTIVE","INDIVIDUAL");
        LocalDateTime oldUpdatedAt=profile.getUpdatedAt();

        when(request.getStatus()).thenReturn(" suspended ");
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(profileRepository.findByAuthUserId(1L))
                .thenReturn(Mono.just(profile));
        when(profileRepository.save(profile)).thenReturn(Mono.just(profile));
        when(addressRepository.findAllByUserProfileId(10L))
                .thenReturn(Flux.empty());

        StepVerifier.create(userProfileService.updateStatus(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getProfileStatus())
                            .isEqualTo("SUSPENDED");
                })
                .verifyComplete();

        assertThat(profile.getStatus()).isEqualTo("SUSPENDED");
        assertThat(profile.getUpdatedAt()).isAfterOrEqualTo(oldUpdatedAt);

        verify(userRepository).findById(1L);
        verify(profileRepository).findByAuthUserId(1L);
        verify(profileRepository).save(profile);
        verify(addressRepository).findAllByUserProfileId(10L);
    }

    @Test
    void updateStatus_whenStatusIsInvalid_shouldReturnValidationException() {
        UpdateUserStatusRequest request=mock(UpdateUserStatusRequest.class);

        when(request.getStatus()).thenReturn("DELETED");

        StepVerifier.create(Mono.defer(()->
                        userProfileService.updateStatus(1L,request)
                ))
                .expectErrorSatisfies(error->
                        assertThat(error).isInstanceOf(ValidationException.class)
                )
                .verify();

        verify(request).getStatus();
        verifyNoInteractions(
                userRepository,
                profileRepository,
                addressRepository
        );
    }

    @Test
    void updateStatus_whenStatusIsNull_shouldReturnValidationException() {
        UpdateUserStatusRequest request=mock(UpdateUserStatusRequest.class);

        when(request.getStatus()).thenReturn(null);

        StepVerifier.create(Mono.defer(()->
                        userProfileService.updateStatus(1L,request)
                ))
                .expectErrorSatisfies(error->
                        assertThat(error).isInstanceOf(ValidationException.class)
                )
                .verify();

        verify(request).getStatus();
        verifyNoInteractions(
                userRepository,
                profileRepository,
                addressRepository
        );
    }

    @Test
    void updateStatus_whenUserDoesNotExist_shouldReturnNotFoundException() {
        UpdateUserStatusRequest request=mock(UpdateUserStatusRequest.class);

        when(request.getStatus()).thenReturn("ACTIVE");
        when(userRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(userProfileService.updateStatus(99L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("User not found");
                })
                .verify();

        verify(userRepository).findById(99L);
        verifyNoInteractions(addressRepository);
        verify(profileRepository,never()).findByAuthUserId(any());
        verify(profileRepository,never()).save(any());
    }

    @Test
    void updateStatus_whenProfileDoesNotExist_shouldReturnNotFoundException() {
        UpdateUserStatusRequest request=mock(UpdateUserStatusRequest.class);
        UserCredential user=user(1L,"ROLE_CUSTOMER");

        when(request.getStatus()).thenReturn("ACTIVE");
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(profileRepository.findByAuthUserId(1L)).thenReturn(Mono.empty());

        StepVerifier.create(userProfileService.updateStatus(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("User profile not found");
                })
                .verify();

        verify(userRepository).findById(1L);
        verify(profileRepository).findByAuthUserId(1L);
        verify(profileRepository,never()).save(any());
        verifyNoInteractions(addressRepository);
    }

    private UpdateProfileRequest profileRequest() {
        UpdateProfileRequest request=mock(UpdateProfileRequest.class);
        AddressRequest addressRequest=mock(AddressRequest.class);

        when(request.getFullName()).thenReturn("  Updated User  ");
        when(request.getPhoneNumber()).thenReturn("  9000000000  ");
        when(request.getDateOfBirth()).thenReturn(LocalDate.of(2000,1,1));
        when(request.getCustomerSegment()).thenReturn("small_business");
        when(request.getAddress()).thenReturn(addressRequest);

        when(addressRequest.getAddressLineOne())
                .thenReturn("  1 Main Road  ");
        when(addressRequest.getAddressLineTwo()).thenReturn(" ");
        when(addressRequest.getCity()).thenReturn("  Hyderabad  ");
        when(addressRequest.getState()).thenReturn("  Telangana  ");
        when(addressRequest.getPostalCode()).thenReturn("  500001  ");
        when(addressRequest.getCountry()).thenReturn("  India  ");

        return request;
    }

    private UserCredential user(Long id,String role) {
        LocalDateTime now=LocalDateTime.now();

        return UserCredential.builder()
                .id(id)
                .fullName("Test User")
                .email("user@test.com")
                .password("encoded-password")
                .role(role)
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private UserProfile profile(
            Long id,
            Long authUserId,
            String status,
            String segment
    ) {
        LocalDateTime now=LocalDateTime.now();

        return UserProfile.builder()
                .id(id)
                .authUserId(authUserId)
                .phoneNumber("9000000000")
                .dateOfBirth(LocalDate.of(2000,1,1))
                .customerSegment(segment)
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Address address(Long id,Long profileId) {
        LocalDateTime now=LocalDateTime.now();

        return Address.builder()
                .id(id)
                .userProfileId(profileId)
                .addressLineOne("1 Main Road")
                .addressLineTwo(null)
                .city("Hyderabad")
                .state("Telangana")
                .postalCode("500001")
                .country("India")
                .primaryAddress(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}