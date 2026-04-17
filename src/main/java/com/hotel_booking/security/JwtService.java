package com.hotel_booking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private SecretKey key;

    @Value("${secretKey}")
    private String secretKey;

    @PostConstruct
    private void init(){
        byte[] keyByte = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(keyByte, "HmacSHA256");
    }

    public String generateToken(String email){
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 1000*60*30))
                .signWith(key)
                .compact();
    }
    
    public String getUserName(String token){
        return extractClaims(token, Claims::getSubject);
    }

    private <T> T extractClaims(String token, Function<Claims,T> claimsResolver) {
        return claimsResolver.apply(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
    }

    public boolean validateToken(String token, UserDetails userDetails){
        final String userName = getUserName(token);
        return (userName.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token, Claims::getExpiration).before(new Date());
    }
}
