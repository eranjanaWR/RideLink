package com.ridelink.account.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.ridelink.account.model.Account;
import com.ridelink.account.model.AccountRole;
import com.ridelink.account.model.AccountStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-ms}") long expirationMs) {
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Account account) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(account.getId())
                .claim("email", account.getEmail())
                .claim("role", account.getRole().name())
                .claim("status", account.getStatus().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String extractAccountId(String token) { return claims(token).getSubject(); }
    public String extractEmail(String token) { return claims(token).get("email", String.class); }
    public AccountRole extractRole(String token) { return AccountRole.valueOf(claims(token).get("role", String.class)); }
    public AccountStatus extractStatus(String token) { return AccountStatus.valueOf(claims(token).get("status", String.class)); }
    public long getExpirationSeconds() { return Math.ceilDiv(expirationMs, 1000); }

    public boolean isTokenValid(String token) {
        try {
            claims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims claims(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null || !claims.getExpiration().after(new Date())) {
            throw new JwtException("JWT expiration is missing or expired");
        }
        return claims;
    }
}
