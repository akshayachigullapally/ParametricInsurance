package org.example.authservice.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.dto.*;
import org.example.authservice.exception.ForbiddenException;
import org.example.authservice.exception.NotFoundException;
import org.example.authservice.exception.ValidationException;
import org.example.authservice.model.*;
import org.example.authservice.repository.AddressRepository;
import org.example.authservice.repository.UserCredentialRepository;
import org.example.authservice.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileService{
    private final UserCredentialRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final AddressRepository addressRepository;
    private final NotificationServiceClient notificationServiceClient;

    @Transactional
    public Mono<UserResponse> saveProfile(Long authUserId,UpdateProfileRequest request){
        return userRepository.findById(authUserId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                .flatMap(user->{
                    if(!"ROLE_CUSTOMER".equals(user.getRole())){
                        return Mono.error(new ForbiddenException(
                                "Profile management is available only for customers"
                        ));
                    }

                    user.setFullName(request.getFullName().trim());
                    user.setUpdatedAt(LocalDateTime.now());

                    return userRepository.save(user)
                            .flatMap(savedUser->saveOrUpdateProfile(savedUser,request)
                                    .flatMap(profile->savePrimaryAddress(
                                            profile,
                                            request.getAddress()
                                    ).then(buildUserResponse(savedUser,profile))));
                })
                .flatMap(response->notificationServiceClient.audit(
                        "PROFILE_UPDATED","USER",authUserId,authUserId,
                        "ROLE_CUSTOMER","Updated profile details"
                ).thenReturn(response));
    }

    public Mono<UserResponse> getMyProfile(Long authUserId){
        return userRepository.findById(authUserId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                .flatMap(user->profileRepository.findByAuthUserId(authUserId)
                        .switchIfEmpty(Mono.error(new NotFoundException(
                                "User profile is not completed"
                        )))
                        .flatMap(profile->buildUserResponse(user,profile)));
    }

    @Transactional
    public Mono<MessageResponse> deactivateProfile(Long authUserId){
        return profileRepository.findByAuthUserId(authUserId)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "User profile not found"
                )))
                .flatMap(profile->{
                    profile.setStatus(UserStatus.INACTIVE.name());
                    profile.setUpdatedAt(LocalDateTime.now());
                    return profileRepository.save(profile);
                })
                .flatMap(saved->notificationServiceClient.audit(
                        "PROFILE_DEACTIVATED","USER",authUserId,authUserId,
                        null,"Deactivated own profile"
                ))
                .thenReturn(MessageResponse.builder()
                        .message("Profile deactivated successfully")
                        .build());
    }

    public Flux<UserResponse> getAllUsers(String status,String segment){
        String validatedStatus=isPresent(status)?validateStatus(status):null;
        String validatedSegment=isPresent(segment)?validateSegment(segment):null;

        Flux<UserProfile> profiles;

        if(validatedStatus!=null&&validatedSegment!=null){
            profiles=profileRepository
                    .findAllByStatusAndCustomerSegment(
                            validatedStatus,
                            validatedSegment
                    );
        }else if(validatedStatus!=null){
            profiles=profileRepository.findAllByStatus(validatedStatus);
        }else if(validatedSegment!=null){
            profiles=profileRepository
                    .findAllByCustomerSegment(validatedSegment);
        }else{
            profiles=profileRepository.findAll();
        }

        return profiles.concatMap(profile->
                userRepository.findById(profile.getAuthUserId())
                        .flatMap(user->buildUserResponse(user,profile))
        );
    }

    public Mono<UserResponse> getUser(Long authUserId){
        return userRepository.findById(authUserId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                .flatMap(user->profileRepository.findByAuthUserId(authUserId)
                        .switchIfEmpty(Mono.error(new NotFoundException(
                                "User profile not found"
                        )))
                        .flatMap(profile->buildUserResponse(user,profile)));
    }

    @Transactional
    public Mono<UserResponse> updateStatus(
            Long authUserId,
            UpdateUserStatusRequest request){

        String status=validateStatus(request.getStatus());

        return userRepository.findById(authUserId)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
                .flatMap(user->profileRepository.findByAuthUserId(authUserId)
                        .switchIfEmpty(Mono.error(new NotFoundException(
                                "User profile not found"
                        )))
                        .flatMap(profile->{
                            profile.setStatus(status);
                            profile.setUpdatedAt(LocalDateTime.now());

                            return profileRepository.save(profile)
                                    .flatMap(savedProfile->
                                            buildUserResponse(user,savedProfile)
                                    );
                        }))
                .flatMap(response->notificationServiceClient.audit(
                        "USER_STATUS_CHANGED","USER",authUserId,null,null,
                        "Changed status of user "+response.getEmail()+" to "+status
                ).thenReturn(response));
    }

    private Mono<UserProfile> saveOrUpdateProfile(
            UserCredential user,
            UpdateProfileRequest request){

        return profileRepository.findByAuthUserId(user.getId())
                .switchIfEmpty(Mono.defer(()->{
                    LocalDateTime now=LocalDateTime.now();

                    return Mono.just(UserProfile.builder()
                            .authUserId(user.getId())
                            .status(UserStatus.ACTIVE.name())
                            .createdAt(now)
                            .updatedAt(now)
                            .build());
                }))
                .flatMap(profile->{
                    profile.setPhoneNumber(request.getPhoneNumber().trim());
                    profile.setDateOfBirth(request.getDateOfBirth());
                    profile.setCustomerSegment(
                            validateSegment(request.getCustomerSegment())
                    );
                    profile.setUpdatedAt(LocalDateTime.now());

                    if(profile.getStatus()==null||profile.getStatus().isBlank()){
                        profile.setStatus(UserStatus.ACTIVE.name());
                    }

                    if(profile.getCreatedAt()==null){
                        profile.setCreatedAt(LocalDateTime.now());
                    }

                    return profileRepository.save(profile);
                });
    }

    private Mono<Address> savePrimaryAddress(
            UserProfile profile,
            AddressRequest request){

        if(request==null){
            return Mono.error(new ValidationException(
                    List.of("Address is required")
            ));
        }

        return addressRepository
                .findByUserProfileIdAndPrimaryAddressTrue(profile.getId())
                .switchIfEmpty(Mono.defer(()->{
                    LocalDateTime now=LocalDateTime.now();

                    return Mono.just(Address.builder()
                            .userProfileId(profile.getId())
                            .primaryAddress(true)
                            .createdAt(now)
                            .updatedAt(now)
                            .build());
                }))
                .flatMap(address->{
                    address.setAddressLineOne(
                            request.getAddressLineOne().trim()
                    );
                    address.setAddressLineTwo(
                            trimToNull(request.getAddressLineTwo())
                    );
                    address.setCity(request.getCity().trim());
                    address.setState(request.getState().trim());
                    address.setPostalCode(request.getPostalCode().trim());
                    address.setCountry(request.getCountry().trim());
                    address.setPrimaryAddress(true);
                    address.setUpdatedAt(LocalDateTime.now());

                    if(address.getCreatedAt()==null){
                        address.setCreatedAt(LocalDateTime.now());
                    }

                    return addressRepository.save(address);
                });
    }

    private Mono<UserResponse> buildUserResponse(
            UserCredential user,
            UserProfile profile){

        return addressRepository.findAllByUserProfileId(profile.getId())
                .map(this::toAddressResponse)
                .collectList()
                .map(addresses->UserResponse.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .enabled(user.getEnabled())
                        .emailVerified(user.getEmailVerified())
                        .phoneNumber(profile.getPhoneNumber())
                        .dateOfBirth(profile.getDateOfBirth())
                        .customerSegment(profile.getCustomerSegment())
                        .profileStatus(profile.getStatus())
                        .addresses(addresses)
                        .createdAt(user.getCreatedAt())
                        .build());
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

    private String validateStatus(String status){
        if(status==null||status.isBlank()){
            throw new ValidationException(
                    List.of("User status is required")
            );
        }

        try{
            return UserStatus.valueOf(
                    status.trim().toUpperCase()
            ).name();
        }catch(IllegalArgumentException e){
            throw new ValidationException(
                    List.of("Status must be ACTIVE, SUSPENDED or INACTIVE")
            );
        }
    }

    private String validateSegment(String segment){
        if(segment==null||segment.isBlank()){
            throw new ValidationException(
                    List.of("Customer segment is required")
            );
        }

        try{
            return CustomerSegment.valueOf(
                    segment.trim().toUpperCase()
            ).name();
        }catch(IllegalArgumentException e){
            throw new ValidationException(
                    List.of(
                            "Customer segment must be INDIVIDUAL, FARMER, "+
                                    "SMALL_BUSINESS or ENTERPRISE"
                    )
            );
        }
    }

    private boolean isPresent(String value){
        return value!=null&&!value.isBlank();
    }

    private String trimToNull(String value){
        if(value==null||value.isBlank()) return null;
        return value.trim();
    }
}