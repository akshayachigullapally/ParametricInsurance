package org.example.authservice.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.example.authservice.model.UserCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil{
    private final SecretKey secretKey;
    private final long expirationTime;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-expiration-ms}") long expirationTime){
        this.secretKey=Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expirationTime=expirationTime;
    }

    public String generateToken(UserCredential user,Long customerId){
        Date issuedAt=new Date();
        Date expiration=new Date(issuedAt.getTime()+expirationTime);

        var builder=Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email",user.getEmail())
                .claim("role",user.getRole())
                .issuedAt(issuedAt)
                .expiration(expiration);

        if(customerId!=null) builder.claim("customerId",customerId);

        return builder.signWith(secretKey).compact();
    }

    public Claims getClaims(String token){
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token){
        try{
            getClaims(token);
            return true;
        }catch(JwtException|IllegalArgumentException e){
            return false;
        }
    }

    public Long getUserId(String token){
        return Long.valueOf(getClaims(token).getSubject());
    }

    public String getEmail(String token){
        return getClaims(token).get("email",String.class);
    }

    public String getRole(String token){
        return getClaims(token).get("role",String.class);
    }

    public Long getCustomerId(String token){
        Number value=getClaims(token).get("customerId",Number.class);
        return value==null?null:value.longValue();
    }

    public long getExpirationSeconds(){
        return expirationTime/1000;
    }
}