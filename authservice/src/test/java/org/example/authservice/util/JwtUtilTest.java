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
    void generateToken_withCustomerId_shouldRoundTripAllClaims(){
        String token=jwtUtil.generateToken(user,7L);

        assertThat(jwtUtil.isValid(token)).isTrue();
        assertThat(jwtUtil.getUserId(token)).isEqualTo(42L);
        assertThat(jwtUtil.getEmail(token)).isEqualTo("user@test.com");
        assertThat(jwtUtil.getRole(token)).isEqualTo("ROLE_CUSTOMER");
        assertThat(jwtUtil.getCustomerId(token)).isEqualTo(7L);
    }

    @Test
    void generateToken_withoutCustomerId_shouldOmitCustomerClaim(){
        String token=jwtUtil.generateToken(user,null);

        assertThat(jwtUtil.getCustomerId(token)).isNull();
    }

    @Test
    void isValid_shouldRejectMalformedToken(){
        assertThat(jwtUtil.isValid("not-a-jwt")).isFalse();
    }

    @Test
    void isValid_shouldRejectTokenSignedWithDifferentKey(){
        JwtUtil other=new JwtUtil(SECRET.replace('T','X'),900_000L);

        assertThat(jwtUtil.isValid(other.generateToken(user,null))).isFalse();
    }

    @Test
    void isValid_shouldRejectExpiredToken(){
        JwtUtil expired=new JwtUtil(SECRET,-1_000L);

        assertThat(jwtUtil.isValid(expired.generateToken(user,null))).isFalse();
    }

    @Test
    void getExpirationSeconds_shouldConvertMilliseconds(){
        assertThat(jwtUtil.getExpirationSeconds()).isEqualTo(900L);
    }
}
