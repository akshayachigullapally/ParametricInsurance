package org.example.financialservice.util;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.example.financialservice.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SecurityUtilTest{

    private UsernamePasswordAuthenticationToken customerAuth(
            Long userId,Long customerId
    ){
        UsernamePasswordAuthenticationToken auth=
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(userId),null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        auth.setDetails(customerId);
        return auth;
    }

    private UsernamePasswordAuthenticationToken adminAuth(Long userId){
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken claimsOfficerAuth(
            Long userId
    ){
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),null,
                List.of(new SimpleGrantedAuthority(
                        "ROLE_CLAIMS_OFFICER")));
    }

    private UsernamePasswordAuthenticationToken underwriterAuth(
            Long userId
    ){
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),null,
                List.of(new SimpleGrantedAuthority(
                        "ROLE_UNDERWRITER")));
    }

    @Test
    void requireCustomerReturnsCustomerId(){
        StepVerifier.create(
                SecurityUtil.requireCustomer()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                customerAuth(5L,5L)))
        )
                .assertNext(id->assertThat(id).isEqualTo(5L))
                .verifyComplete();
    }

    @Test
    void requireCustomerRejectsUnderwriter(){
        StepVerifier.create(
                SecurityUtil.requireCustomer()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                underwriterAuth(3L)))
        )
                .expectError(ForbiddenException.class)
                .verify();
    }
}
