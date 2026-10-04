package com.ridelink.ride.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenValidator {

    private final SecretKey key;

    public JwtTokenValidator(@Value("${security.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthenticatedUser validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String accountId = claims.getSubject();
        String roleClaim = claims.get("role", String.class);
        String statusClaim = claims.get("status", String.class);
        Date expiration = claims.getExpiration();

        if (accountId == null || accountId.isBlank()
                || roleClaim == null || roleClaim.isBlank()
                || statusClaim == null || statusClaim.isBlank()
                || expiration == null || !expiration.after(new Date())) {
            throw new JwtException("Token is missing required claims or has expired");
        }

        try {
            return new AuthenticatedUser(
                    accountId,
                    claims.get("email", String.class),
                    AccountRole.valueOf(roleClaim),
                    AccountStatus.valueOf(statusClaim));
        } catch (IllegalArgumentException exception) {
            throw new JwtException("Token contains an unsupported role or status", exception);
        }
    }
}
