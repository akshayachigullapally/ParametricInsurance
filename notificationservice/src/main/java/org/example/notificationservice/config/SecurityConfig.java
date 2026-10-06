package org.example.notificationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * CSRF must stay off: other services POST to /internal/** without a browser session.
 * If this class is not loaded (e.g. a stale IDE build), Spring Boot's default security
 * answers those POSTs with 403 Forbidden and no notification is ever created, so the
 * startup log line below is the quick way to confirm it is active.
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig{
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http){
        log.info("Notification security loaded: CSRF off, /internal/** open to services with the gateway key");
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(e->e.pathMatchers("/actuator","/actuator/**","/internal/**").permitAll()
                        .anyExchange().authenticated())
                .build();
    }
}
