package com.ridelink.ride.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenValidatorTest {

    private static final String SECRET =
            "test-only-jwt-secret-that-is-at-least-32-bytes-long";

    private JwtTokenValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JwtTokenValidator(SECRET);
    }

    @Test
    void extractsCompleteAuthenticatedPrincipal() {
        AuthenticatedUser user = validator.validate(token(
                "account-1",
                "passenger@example.com",
                "PASSENGER",
                "ACTIVE",
                true));

        assertThat(user.accountId()).isEqualTo("account-1");
        assertThat(user.email()).isEqualTo("passenger@example.com");
        assertThat(user.role()).isEqualTo(AccountRole.PASSENGER);
        assertThat(user.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void rejectsUnsupportedRole() {
        assertThatThrownBy(() -> validator.validate(token(
                "account-1", "x@example.com", "SUPERUSER", "ACTIVE", true)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnsupportedStatus() {
        assertThatThrownBy(() -> validator.validate(token(
                "account-1", "x@example.com", "DRIVER", "DELETED", true)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingExpiration() {
        assertThatThrownBy(() -> validator.validate(token(
                "account-1", "x@example.com", "DRIVER", "ACTIVE", false)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingSubject() {
        assertThatThrownBy(() -> validator.validate(token(
                null, "x@example.com", "DRIVER", "ACTIVE", true)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingRole() {
        assertThatThrownBy(() -> validator.validate(token(
                "account-1", "x@example.com", null, "ACTIVE", true)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingStatus() {
        assertThatThrownBy(() -> validator.validate(token(
                "account-1", "x@example.com", "DRIVER", null, true)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void acceptsMissingOptionalEmailWithoutLosingIdentity() {
        AuthenticatedUser user = validator.validate(token(
                "account-1", null, "ADMIN", "ACTIVE", true));

        assertThat(user.accountId()).isEqualTo("account-1");
        assertThat(user.email()).isNull();
    }

    private String token(
            String accountId,
            String email,
            String role,
            String status,
            boolean includeExpiration
    ) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder()
                .subject(accountId)
                .claim("role", role)
                .claim("status", status)
                .issuedAt(Date.from(Instant.now()));
        if (email != null) {
            builder.claim("email", email);
        }
        if (includeExpiration) {
            builder.expiration(Date.from(Instant.now().plusSeconds(60)));
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }
}
