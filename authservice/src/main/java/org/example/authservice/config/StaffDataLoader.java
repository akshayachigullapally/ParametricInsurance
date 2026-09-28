package org.example.authservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.authservice.model.UserCredential;
import org.example.authservice.repository.UserCredentialRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaffDataLoader implements ApplicationRunner{
    private final UserCredentialRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args){
        List<UserCredential> staff=List.of(
                createUser(
                        "CityShield Admin",
                        "admin@gmail.com",
                        "admin@123",
                        "ROLE_ADMIN"
                ),
                createUser(
                        "Underwriter Anil",
                        "anil@gmail.com",
                        "12345678",
                        "ROLE_UNDERWRITER"
                ),
                createUser(
                        "Underwriter Sandhya",
                        "sandhya@gmail.com",
                        "12345678",
                        "ROLE_UNDERWRITER"
                ),
                createUser(
                        "Claims Officer Shiva",
                        "shiva@gmail.com",
                        "12345678",
                        "ROLE_CLAIMS_OFFICER"
                ),
                createUser(
                        "Claims Officer Pallavi",
                        "pallavi@gmail.com",
                        "12345678",
                        "ROLE_CLAIMS_OFFICER"
                )
        );

        Flux.fromIterable(staff)
                .concatMap(this::saveIfNotExists)
                .doOnComplete(()->log.info("Staff data initialization completed"))
                .doOnError(error->log.error("Staff data initialization failed",error))
                .subscribe();
    }

    private Mono<UserCredential> saveIfNotExists(UserCredential user){
        return userRepository.findByEmailIgnoreCase(user.getEmail())
                .flatMap(existing->{
                    if(!user.getRole().equals(existing.getRole())){
                        existing.setRole(user.getRole());
                        existing.setUpdatedAt(LocalDateTime.now());

                        return userRepository.save(existing)
                                .doOnSuccess(updated->log.info(
                                        "Updated staff account: {} role {}",
                                        updated.getEmail(),
                                        updated.getRole()
                                ));
                    }

                    log.info("Staff account already exists: {}",user.getEmail());
                    return Mono.just(existing);
                })
                .switchIfEmpty(Mono.defer(()->{
                    user.setPassword(passwordEncoder.encode(user.getPassword()));

                    return userRepository.save(user)
                            .doOnSuccess(saved->log.info(
                                    "Created staff account: {} with role {}",
                                    saved.getEmail(),
                                    saved.getRole()
                            ));
                }));
    }

    private UserCredential createUser(String fullName,String email,
                                      String password,String role){
        LocalDateTime now=LocalDateTime.now();

        return UserCredential.builder()
                .fullName(fullName)
                .email(email.toLowerCase())
                .password(password)
                .role(role)
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}