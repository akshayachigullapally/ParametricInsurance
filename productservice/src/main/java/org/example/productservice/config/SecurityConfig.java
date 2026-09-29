package org.example.productservice.config;

import lombok.RequiredArgsConstructor;
import org.example.productservice.filter.GatewayAuthenticationFilter;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
                .securityContextRepository(
                        NoOpServerSecurityContextRepository.getInstance()
                )
                .exceptionHandling(handling->handling
                        .authenticationEntryPoint((exchange,exception)->{
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        })
                        .accessDeniedHandler((exchange,exception)->{
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        })
                )
                .authorizeExchange(auth->auth
                        .pathMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                        .pathMatchers("/actuator","/actuator/**").permitAll()

                        .pathMatchers(
                                HttpMethod.GET,
                                "/internal/policies/**",
                                "/internal/products/**"
                        ).permitAll()

                        .pathMatchers(HttpMethod.POST,
                                "/internal/products/verified-event"
                        ).permitAll()

                        .pathMatchers(HttpMethod.GET,
                                "/api/products",
                                "/api/products/*",
                                "/api/products/category/*",
                                "/api/products/measurement-status",
                                "/api/products/{productId:[0-9]+}"
                        ).permitAll()

                        .pathMatchers(HttpMethod.GET,
                                "/api/products/admin/all",
                                "/api/products/admin/{productId:[0-9]+}",
                                "/api/products/admin/{productId:[0-9]+}/threshold-history",
                                "/api/products/admin/measurement-status"
                        ).hasRole("ADMIN")

                        .pathMatchers(HttpMethod.POST,
                                "/api/products",
                                "/api/products/admin/{productId:[0-9]+}/sync-trigger"
                        ).hasRole("ADMIN")

                        .pathMatchers(HttpMethod.PUT,
                                "/api/products/{productId:[0-9]+}",
                                "/api/products/{productId:[0-9]+}/status",
                                "/api/products/admin/{productId:[0-9]+}/threshold"
                        ).hasRole("ADMIN")

                        .pathMatchers(HttpMethod.DELETE,
                                "/api/products/{productId:[0-9]+}"
                        ).hasRole("ADMIN")

                        .pathMatchers(HttpMethod.POST,
                                "/api/policies",
                                "/api/policies/custom"
                        ).hasRole("CUSTOMER")

                        .pathMatchers(HttpMethod.GET,
                                "/api/policies/me",
                                "/api/policies/{policyId:[0-9]+}"
                        ).hasRole("CUSTOMER")

                        .pathMatchers(HttpMethod.PUT,
                                "/api/policies/{policyId:[0-9]+}/cancel"
                        ).hasRole("CUSTOMER")

                        .pathMatchers(HttpMethod.POST,
                                "/api/policies/{policyId:[0-9]+}/renew"
                        ).hasRole("CUSTOMER")

                        .pathMatchers(HttpMethod.PUT,
                                "/api/policies/{policyId:[0-9]+}/auto-renew"
                        ).hasRole("CUSTOMER")

                        .pathMatchers(HttpMethod.GET,
                                "/api/policies/{policyId:[0-9]+}/certificate"
                        ).hasRole("CUSTOMER")

                        .pathMatchers(HttpMethod.GET,
                                "/api/policies/admin/{policyId:[0-9]+}/certificate"
                        ).hasAnyRole("ADMIN","UNDERWRITER")

                        .pathMatchers(HttpMethod.GET,
                                "/api/policies/{policyId:[0-9]+}/history",
                                "/api/policies/{policyId:[0-9]+}/snapshot"
                        ).hasAnyRole("CUSTOMER","UNDERWRITER","ADMIN")

                        .pathMatchers(HttpMethod.GET,
                                "/api/policies/review/pending",
                                "/api/policies/review/history"
                        ).hasAnyRole("UNDERWRITER","ADMIN")

                        .pathMatchers(HttpMethod.PUT,
                                "/api/policies/{policyId:[0-9]+}/approve",
                                "/api/policies/{policyId:[0-9]+}/reject"
                        ).hasAnyRole("UNDERWRITER","ADMIN")

                        .pathMatchers(HttpMethod.GET,
                                "/api/policies/admin/all",
                                "/api/policies/admin/zone-exposure",
                                "/api/policies/admin/{policyId:[0-9]+}"
                        ).hasAnyRole("ADMIN", "UNDERWRITER")

                        .anyExchange().denyAll()
                )
                .addFilterAt(
                        gatewayAuthenticationFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                .build();
    }
}