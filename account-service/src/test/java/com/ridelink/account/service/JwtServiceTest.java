package com.ridelink.account.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

import com.ridelink.account.model.Account;
import com.ridelink.account.model.AccountRole;
import com.ridelink.account.model.AccountStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "test-only-secret-with-at-least-thirty-two-bytes-123456";
    private JwtService service;
    private Account account;

    @BeforeEach void setUp() {
        service = new JwtService(SECRET, 3_600_000);
        LocalDateTime now = LocalDateTime.of(2026, 9, 30, 12, 0);
        account = new Account("account-uuid", "Dineth", "dineth@example.com", "0771234567",
                "bcrypt-hash", AccountRole.DRIVER, AccountStatus.ACTIVE, now, now);
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
    }

    @Test void generatedTokenIsNonblank() { assertFalse(service.generateToken(account).isBlank()); }
    @Test void subjectIsAccountId() { assertEquals("account-uuid", claims(service.generateToken(account)).getSubject()); }
    @Test void emailClaimIsCorrect() { assertEquals("dineth@example.com", claims(service.generateToken(account)).get("email")); }
    @Test void roleClaimIsCorrect() { assertEquals("DRIVER", claims(service.generateToken(account)).get("role")); }
    @Test void statusClaimIsCorrect() { assertEquals("ACTIVE", claims(service.generateToken(account)).get("status")); }
    @Test void issuedAtIsPresent() { assertNotNull(claims(service.generateToken(account)).getIssuedAt()); }
    @Test void expirationIsPresent() { assertNotNull(claims(service.generateToken(account)).getExpiration()); }
    @Test void expirationIsLaterThanIssuedAt() {
        Claims claims = claims(service.generateToken(account));
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }
    @Test void validTokenPassesValidation() { assertTrue(service.isTokenValid(service.generateToken(account))); }
    @Test void tamperedTokenFailsValidation() {
        String[] parts = service.generateToken(account).split("\\.");
        String payload = (parts[1].charAt(0) == 'A' ? "B" : "A") + parts[1].substring(1);
        assertFalse(service.isTokenValid(parts[0] + "." + payload + "." + parts[2]));
    }
    @Test void expiredTokenFailsValidation() {
        String expired = Jwts.builder().subject("account-uuid")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        assertFalse(service.isTokenValid(expired));
    }
    @Test void tokenWithoutExpirationFailsValidation() {
        String noExpiry = Jwts.builder().subject("account-uuid")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        assertFalse(service.isTokenValid(noExpiry));
    }
    @Test void accountIdCanBeExtracted() { assertEquals("account-uuid", service.extractAccountId(service.generateToken(account))); }
    @Test void emailCanBeExtracted() { assertEquals("dineth@example.com", service.extractEmail(service.generateToken(account))); }
    @Test void roleCanBeExtracted() { assertEquals(AccountRole.DRIVER, service.extractRole(service.generateToken(account))); }
    @Test void statusCanBeExtracted() { assertEquals(AccountStatus.ACTIVE, service.extractStatus(service.generateToken(account))); }
    @Test void noSensitiveClaimsAreIncluded() {
        Claims claims = claims(service.generateToken(account));
        assertFalse(claims.containsKey("password"));
        assertFalse(claims.containsKey("passwordHash"));
        assertFalse(claims.containsKey("phone"));
    }
    @Test void anotherSecretCannotValidateToken() {
        JwtService other = new JwtService("another-test-only-secret-with-at-least-thirty-two-bytes", 3_600_000);
        assertFalse(other.isTokenValid(service.generateToken(account)));
    }
    @Test void expiresInIsSeconds() { assertEquals(3600, service.getExpirationSeconds()); }
    @Test void nonPositiveExpiryIsRejected() { assertThrows(IllegalArgumentException.class, () -> new JwtService(SECRET, 0)); }
    @Test void shortHmacSecretIsRejected() { assertThrows(WeakKeyException.class, () -> new JwtService("short", 3600000)); }
}
