package com.ridelink.account.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import com.ridelink.account.dto.LoginRequest;
import com.ridelink.account.dto.LoginResponse;
import com.ridelink.account.exception.AccountDisabledException;
import com.ridelink.account.exception.AccountSuspendedException;
import com.ridelink.account.exception.InvalidCredentialsException;
import com.ridelink.account.model.Account;
import com.ridelink.account.model.AccountRole;
import com.ridelink.account.model.AccountStatus;
import com.ridelink.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String HASH = ENCODER.encode("Password123");
    @Mock AccountRepository repository;
    @Mock JwtService jwtService;
    private AuthService service;

    @BeforeEach void setUp() { service = new AuthService(repository, ENCODER, jwtService); }

    private Account account(AccountRole role, AccountStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 30, 12, 0);
        return new Account("id-123", "Dineth Perera", "dineth@example.com", "0771234567", HASH,
                role, status, now, now);
    }

    private LoginRequest request() { return new LoginRequest("dineth@example.com", "Password123"); }

    private Account existing(AccountRole role, AccountStatus status) {
        Account account = account(role, status);
        when(repository.findByEmail("dineth@example.com")).thenReturn(Optional.of(account));
        return account;
    }

    private void token() {
        when(jwtService.generateToken(any(Account.class))).thenReturn("signed.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
    }

    @Test void activePassengerLoginSucceeds() {
        existing(AccountRole.PASSENGER, AccountStatus.ACTIVE); token();
        LoginResponse response = service.login(request());
        assertEquals(AccountRole.PASSENGER, response.account().role());
        assertEquals("signed.jwt.token", response.accessToken());
        verify(repository, never()).save(any());
    }
    @Test void activeDriverLoginSucceeds() {
        existing(AccountRole.DRIVER, AccountStatus.ACTIVE); token();
        assertEquals(AccountRole.DRIVER, service.login(request()).account().role());
    }
    @Test void trimsEmail() {
        existing(AccountRole.PASSENGER, AccountStatus.ACTIVE); token();
        service.login(new LoginRequest("  dineth@example.com  ", "Password123"));
        verify(repository).findByEmail("dineth@example.com");
    }
    @Test void lowercasesEmail() {
        existing(AccountRole.PASSENGER, AccountStatus.ACTIVE); token();
        service.login(new LoginRequest("DINETH@EXAMPLE.COM", "Password123"));
        verify(repository).findByEmail("dineth@example.com");
    }
    @Test void validPasswordMatchesBcrypt() {
        existing(AccountRole.PASSENGER, AccountStatus.ACTIVE); token();
        assertEquals("Bearer", service.login(request()).tokenType());
        assertTrue(ENCODER.matches("Password123", HASH));
    }
    @Test void wrongPasswordIsUnauthorized() {
        existing(AccountRole.PASSENGER, AccountStatus.ACTIVE);
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("dineth@example.com", "wrong")));
    }
    @Test void unknownEmailIsUnauthorized() {
        when(repository.findByEmail("dineth@example.com")).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> service.login(request()));
    }
    @Test void wrongPasswordAndUnknownEmailHaveSameMessage() {
        existing(AccountRole.PASSENGER, AccountStatus.ACTIVE);
        String wrong = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("dineth@example.com", "wrong"))).getMessage();
        when(repository.findByEmail("dineth@example.com")).thenReturn(Optional.empty());
        String unknown = assertThrows(InvalidCredentialsException.class, () -> service.login(request())).getMessage();
        assertEquals("Invalid email or password", wrong);
        assertEquals(wrong, unknown);
    }
    @Test void suspendedAccountIsForbidden() {
        existing(AccountRole.PASSENGER, AccountStatus.SUSPENDED);
        assertThrows(AccountSuspendedException.class, () -> service.login(request()));
    }
    @Test void disabledAccountIsForbidden() {
        existing(AccountRole.PASSENGER, AccountStatus.DISABLED);
        assertThrows(AccountDisabledException.class, () -> service.login(request()));
    }
    @Test void noJwtForWrongCredentials() {
        existing(AccountRole.PASSENGER, AccountStatus.ACTIVE);
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("dineth@example.com", "wrong")));
        verifyNoInteractions(jwtService);
    }
    @Test void noJwtForSuspendedAccount() {
        existing(AccountRole.PASSENGER, AccountStatus.SUSPENDED);
        assertThrows(AccountSuspendedException.class, () -> service.login(request()));
        verifyNoInteractions(jwtService);
    }
    @Test void noJwtForDisabledAccount() {
        existing(AccountRole.PASSENGER, AccountStatus.DISABLED);
        assertThrows(AccountDisabledException.class, () -> service.login(request()));
        verifyNoInteractions(jwtService);
    }
    @Test void responseDoesNotContainPasswordHash() {
        existing(AccountRole.PASSENGER, AccountStatus.ACTIVE); token();
        LoginResponse response = service.login(request());
        assertFalse(java.util.Arrays.stream(response.account().getClass().getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("password")));
    }
    @Test void wrongPasswordOnSuspendedAccountDoesNotRevealStatus() {
        existing(AccountRole.PASSENGER, AccountStatus.SUSPENDED);
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("dineth@example.com", "wrong")));
    }
    @Test void jwtReceivesAuthenticatedAccount() {
        Account account = existing(AccountRole.DRIVER, AccountStatus.ACTIVE); token();
        service.login(request());
        verify(jwtService).generateToken(account);
    }
    @Test void expirySecondsComeFromJwtConfiguration() {
        existing(AccountRole.PASSENGER, AccountStatus.ACTIVE); token();
        assertEquals(3600, service.login(request()).expiresIn());
    }
    @Test void loginRequestStringRedactsPassword() {
        assertFalse(request().toString().contains("Password123"));
    }
}
