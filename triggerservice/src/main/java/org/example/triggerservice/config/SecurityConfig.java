package org.example.triggerservice.config;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.filter.GatewayAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig{
    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http){
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(exchange->exchange
                        .pathMatchers("/actuator","/actuator/**").permitAll()
                        .pathMatchers(
                                "/internal/triggers/**"
                        ).permitAll()
                        .pathMatchers("/api/triggers/**").authenticated()
                        .anyExchange().denyAll())
                .addFilterAt(gatewayAuthenticationFilter,SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}