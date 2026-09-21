package org.example.apigateway.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil{
    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret){
        this.secretKey=Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public Claims parseToken(String token){
        try{
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }catch(JwtException|IllegalArgumentException e){
            throw new IllegalArgumentException("Invalid or expired JWT token",e);
        }
    }

    public Long extractUserId(Claims claims){
        String subject=claims.getSubject();

        if(subject==null||subject.isBlank()){
            throw new IllegalArgumentException("JWT does not contain user id");
        }

        try{
            return Long.valueOf(subject);
        }catch(NumberFormatException e){
            throw new IllegalArgumentException("JWT contains invalid user id",e);
        }
    }

    public String extractEmail(Claims claims){
        return claims.get("email",String.class);
    }

    public String extractRole(Claims claims){
        return claims.get("role",String.class);
    }

    public Long extractCustomerId(Claims claims){
        Object value=claims.get("customerId");

        if(value==null) return null;
        if(value instanceof Number number) return number.longValue();

        try{
            return Long.valueOf(value.toString());
        }catch(NumberFormatException e){
            throw new IllegalArgumentException("Invalid customer id claim",e);
        }
    }

    public boolean isExpired(Claims claims){
        Date expiration=claims.getExpiration();
        return expiration==null||expiration.before(new Date());
    }
}