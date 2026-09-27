package org.example.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig{
@Bean SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http){return http.csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable).formLogin(ServerHttpSecurity.FormLoginSpec::disable).authorizeExchange(e->e.pathMatchers("/actuator","/actuator/**","/internal/**").permitAll().anyExchange().authenticated()).build();}
}