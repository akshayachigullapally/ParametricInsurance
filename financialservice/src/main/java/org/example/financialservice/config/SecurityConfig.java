package org.example.financialservice.config;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class SecurityConfig{
    @Bean
    public ReactiveUserDetailsService userDetailsService(){
        return username->Mono.just(
                User.withUsername("_internal")
                        .password("{noop}disabled")
                        .roles("NONE")
                        .build()
        );
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http
    ){
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(handling->handling
                        .authenticationEntryPoint(
                                (exchange,exception)->writeJson(
                                        exchange,
                                        HttpStatus.UNAUTHORIZED,
                                        "Authentication required"
                                )
                        )
                        .accessDeniedHandler(
                                (exchange,denied)->writeJson(
                                        exchange,
                                        HttpStatus.FORBIDDEN,
                                        "Access denied"
                                )
                        )
                )
                .authorizeExchange(exchange->exchange
                        .anyExchange()
                        .permitAll()
                )
                .build();
    }

    private Mono<Void> writeJson(
            ServerWebExchange exchange,
            HttpStatus status,
            String message
    ){
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse()
                .getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse()
                .getHeaders()
                .setCacheControl("no-store");

        String body="{\"status\":"+status.value()+
                ",\"error\":\""+status.getReasonPhrase()+
                "\",\"message\":\""+message+"\"}";

        return exchange.getResponse().writeWith(
                Mono.just(
                        exchange.getResponse()
                                .bufferFactory()
                                .wrap(
                                        body.getBytes(
                                                StandardCharsets.UTF_8
                                        )
                                )
                )
        );
    }
}