package com.hotel_booking.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET_KEY = "12345678901234567890123456789012";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.invokeMethod(jwtService, "init");
    }

    @Test
    void generateTokenCreatesSignedTokenWithEmailSubject() {
        String token = jwtService.generateToken("jane.doe@example.com");

        assertThat(token).isNotBlank();
        assertThat(jwtService.getUserName(token)).isEqualTo("jane.doe@example.com");
    }

    @Test
    void validateTokenReturnsTrueWhenSubjectMatchesAndTokenIsNotExpired() {
        String token = jwtService.generateToken("jane.doe@example.com");
        UserDetails userDetails = userDetails("jane.doe@example.com");

        assertThat(jwtService.validateToken(token, userDetails)).isTrue();
    }

    @Test
    void validateTokenReturnsFalseWhenSubjectDoesNotMatchUserDetails() {
        String token = jwtService.generateToken("jane.doe@example.com");
        UserDetails userDetails = userDetails("other@example.com");

        assertThat(jwtService.validateToken(token, userDetails)).isFalse();
    }

    @Test
    void validateTokenReturnsFalseWhenTokenIsExpired() {
        String expiredToken = tokenWithExpiration(new Date(System.currentTimeMillis() - 60_000));
        UserDetails userDetails = userDetails("jane.doe@example.com");

        assertThat(jwtService.validateToken(expiredToken, userDetails)).isFalse();
    }

    @Test
    void getUserNameThrowsWhenTokenIsExpired() {
        String expiredToken = tokenWithExpiration(new Date(System.currentTimeMillis() - 60_000));

        assertThatThrownBy(() -> jwtService.getUserName(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void getUserNameThrowsWhenTokenSignatureDoesNotMatchConfiguredSecret() {
        SecretKey otherKey = new SecretKeySpec(
                "abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        String token = Jwts.builder()
                .subject("jane.doe@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        assertThatThrownBy(() -> jwtService.getUserName(token))
                .isInstanceOf(RuntimeException.class);
    }

    private String tokenWithExpiration(Date expiration) {
        return Jwts.builder()
                .subject("jane.doe@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(expiration)
                .signWith(secretKey())
                .compact();
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private UserDetails userDetails(String username) {
        return User.withUsername(username)
                .password("secret")
                .authorities("CUSTOMER")
                .build();
    }
}
