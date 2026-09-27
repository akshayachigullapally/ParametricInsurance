package org.example.authservice.config;

import lombok.RequiredArgsConstructor;
import org.example.authservice.filter.GatewayAuthenticationFilter;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
                .securityContextRepository(
                        NoOpServerSecurityContextRepository.getInstance()
                )
                .authorizeExchange(auth->auth
                        .pathMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                        .pathMatchers("/actuator","/actuator/**").permitAll()
                        .pathMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/verify-email",
                                "/api/auth/verify-email/resend-by-email",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()
                        .pathMatchers(HttpMethod.POST,
                                "/api/auth/logout",
                                "/api/auth/verify-email/resend"
                        ).authenticated()
                        .pathMatchers(HttpMethod.GET,
                                "/api/auth/me",
                                "/api/auth/sessions"
                        ).authenticated()
                        .pathMatchers(HttpMethod.DELETE,
                                "/api/auth/sessions/**"
                        ).authenticated()
                        .pathMatchers(HttpMethod.GET,
                                "/api/users/me"
                        ).authenticated()
                        .pathMatchers(HttpMethod.PUT,
                                "/api/users/me"
                        ).hasRole("CUSTOMER")
                        .pathMatchers(HttpMethod.DELETE,
                                "/api/users/me"
                        ).hasRole("CUSTOMER")
                        .pathMatchers(HttpMethod.GET,
                                "/api/users"
                        ).hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET,
                                "/api/users/{userId:[0-9]+}"
                        ).hasAnyRole("ADMIN","UNDERWRITER","CLAIMS_OFFICER")
                        .pathMatchers(HttpMethod.PUT,
                                "/api/users/*/status"
                        ).hasRole("ADMIN")
                        .anyExchange().denyAll()
                )
                .addFilterAt(
                        gatewayAuthenticationFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                .build();
    }
}