package org.example.claimsservice.config;

import lombok.RequiredArgsConstructor;
import org.example.claimsservice.filter.GatewayAuthenticationFilter;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.*;
import org.springframework.security.web.server.*;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig{
    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http){
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth->auth
                        .pathMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                        .pathMatchers("/actuator","/actuator/**").permitAll()
                        .pathMatchers(HttpMethod.POST,"/api/claims").hasRole("CUSTOMER")
                        .pathMatchers(HttpMethod.GET,"/api/claims/me").hasRole("CUSTOMER")
                        .pathMatchers(HttpMethod.GET,
                                "/api/claims/{claimId:[0-9]+}",
                                "/api/claims/{claimId:[0-9]+}/timeline"
                        ).hasRole("CUSTOMER")
                        .pathMatchers(HttpMethod.PUT,
                                "/api/claims/{claimId:[0-9]+}/cancel"
                        ).hasRole("CUSTOMER")
                        .pathMatchers(HttpMethod.POST,
                                "/api/claims/{claimId:[0-9]+}/documents"
                        ).hasRole("CUSTOMER")
                        .pathMatchers(HttpMethod.GET,
                                "/api/claims/{claimId:[0-9]+}/documents",
                                "/api/claims/{claimId:[0-9]+}/documents/{documentId:[0-9]+}"
                        ).hasRole("CUSTOMER")
                        .pathMatchers(HttpMethod.GET,
                                "/api/claims/review/{claimId:[0-9]+}/documents",
                                "/api/claims/review/{claimId:[0-9]+}/documents/{documentId:[0-9]+}"
                        ).hasAnyRole("CLAIMS_OFFICER","ADMIN")
                        .pathMatchers(HttpMethod.GET,
                                "/api/claims/review/manual",
                                "/api/claims/review/manual/{claimId:[0-9]+}"
                        ).hasAnyRole("CLAIMS_OFFICER","ADMIN")
                        .pathMatchers(HttpMethod.GET,
                                "/api/claims/review/submitted",
                                "/api/claims/review/history",
                                "/api/claims/review/{claimId:[0-9]+}"
                        ).hasAnyRole("CLAIMS_OFFICER","ADMIN")
                        .pathMatchers(HttpMethod.PUT,
                                "/api/claims/{claimId:[0-9]+}/start-review",
                                "/api/claims/{claimId:[0-9]+}/approve",
                                "/api/claims/{claimId:[0-9]+}/reject",
                                "/api/claims/{claimId:[0-9]+}/settle"
                        ).hasAnyRole("CLAIMS_OFFICER","ADMIN")
                        .pathMatchers(HttpMethod.GET,
                                "/api/claims/admin/all",
                                "/api/claims/admin/{claimId:[0-9]+}",
                                "/api/claims/admin/{claimId:[0-9]+}/timeline"
                        ).hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET,
                                "/internal/claims/**"
                        ).permitAll()
                        .pathMatchers(HttpMethod.POST,
                                "/internal/claims",
                                "/internal/claims/from-trigger-event"
                        ).permitAll()
                        .pathMatchers(HttpMethod.PUT,
                                "/internal/claims/{claimId:[0-9]+}/settle"
                        ).permitAll()
                        .anyExchange().denyAll()
                )
                .addFilterAt(
                        gatewayAuthenticationFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                .build();
    }
}