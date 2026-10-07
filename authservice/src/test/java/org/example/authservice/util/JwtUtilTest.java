package org.example.authservice.util;

import org.example.authservice.model.UserCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest{
    private static final String SECRET="TestSecretKeyThatIsDefinitelyLongerThanThirtyTwoCharacters123456";

    private JwtUtil jwtUtil;
    private UserCredential user;

    @BeforeEach
    void setUp(){
        jwtUtil=new JwtUtil(SECRET,900_000L);
        user=UserCredential.builder()
                .id(42L)
                .email("user@test.com")
                .role("ROLE_CUSTOMER")
                .build();
    }

    @Test
    void getExpirationSeconds_shouldConvertMilliseconds(){
        assertThat(jwtUtil.getExpirationSeconds()).isEqualTo(900L);
    }
}
