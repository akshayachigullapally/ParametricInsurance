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
    void requireCustomerRejectsAdmin(){
        StepVerifier.create(
                SecurityUtil.requireCustomer()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                adminAuth(1L)))
        )
                .expectError(ForbiddenException.class)
                .verify();
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

    @Test
    void requireAdminAcceptsAdmin(){
        StepVerifier.create(
                SecurityUtil.requireAdmin()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                adminAuth(1L)))
        )
                .verifyComplete();
    }

    @Test
    void requireAdminRejectsCustomer(){
        StepVerifier.create(
                SecurityUtil.requireAdmin()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                customerAuth(5L,5L)))
        )
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void requireClaimsOfficerOrAdminAcceptsAdmin(){
        StepVerifier.create(
                SecurityUtil.requireClaimsOfficerOrAdmin()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                adminAuth(1L)))
        )
                .verifyComplete();
    }

    @Test
    void requireClaimsOfficerOrAdminAcceptsClaimsOfficer(){
        StepVerifier.create(
                SecurityUtil.requireClaimsOfficerOrAdmin()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                claimsOfficerAuth(2L)))
        )
                .verifyComplete();
    }

    @Test
    void requireClaimsOfficerOrAdminRejectsCustomer(){
        StepVerifier.create(
                SecurityUtil.requireClaimsOfficerOrAdmin()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                customerAuth(5L,5L)))
        )
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void customerIdReturnsFromDetails(){
        StepVerifier.create(
                SecurityUtil.customerId()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                customerAuth(5L,5L)))
        )
                .assertNext(id->assertThat(id).isEqualTo(5L))
                .verifyComplete();
    }

    @Test
    void roleReturnsCorrectRole(){
        StepVerifier.create(
                SecurityUtil.role()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                adminAuth(1L)))
        )
                .assertNext(role->assertThat(role)
                        .isEqualTo("ROLE_ADMIN"))
                .verifyComplete();
    }

    @Test
    void userIdReturnsCorrectId(){
        StepVerifier.create(
                SecurityUtil.userId()
                        .contextWrite(
                                ReactiveSecurityContextHolder
                                        .withAuthentication(
                                                adminAuth(42L)))
        )
                .assertNext(id->assertThat(id).isEqualTo(42L))
                .verifyComplete();
    }
}
