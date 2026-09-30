package com.ridelink.account.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.dto.RegisterAccountRequest;
import com.ridelink.account.exception.DuplicateAccountException;
import com.ridelink.account.exception.InvalidRegistrationRoleException;
import com.ridelink.account.model.Account;
import com.ridelink.account.model.AccountRole;
import com.ridelink.account.model.AccountStatus;
import com.ridelink.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock AccountRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(repository, encoder);
        lenient().when(repository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RegisterAccountRequest request(AccountRole role) {
        return new RegisterAccountRequest("Dineth Perera", "dineth@example.com", "0771234567", "Password123", role);
    }

    private Account savedAccount(RegisterAccountRequest request) {
        service.register(request);
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test void passengerRegistrationSucceeds() {
        assertEquals(AccountRole.PASSENGER, service.register(request(AccountRole.PASSENGER)).role());
    }

    @Test void driverRegistrationSucceeds() {
        assertEquals(AccountRole.DRIVER, service.register(request(AccountRole.DRIVER)).role());
    }

    @Test void generatesUuidString() {
        assertDoesNotThrow(() -> UUID.fromString(savedAccount(request(AccountRole.PASSENGER)).getId()));
    }

    @Test void newAccountIsActive() {
        assertEquals(AccountStatus.ACTIVE, savedAccount(request(AccountRole.PASSENGER)).getStatus());
    }

    @Test void setsCreatedAt() {
        assertNotNull(savedAccount(request(AccountRole.PASSENGER)).getCreatedAt());
    }

    @Test void setsUpdatedAt() {
        assertNotNull(savedAccount(request(AccountRole.PASSENGER)).getUpdatedAt());
    }

    @Test void timestampsStartEqual() {
        Account account = savedAccount(request(AccountRole.PASSENGER));
        assertEquals(account.getCreatedAt(), account.getUpdatedAt());
        assertFalse(account.getCreatedAt().isAfter(LocalDateTime.now()));
    }

    @Test void trimsEmailBeforeLookupAndSave() {
        Account account = savedAccount(new RegisterAccountRequest("Dineth", "  user@test.com  ", "0771234567", "Password123", AccountRole.PASSENGER));
        verify(repository).existsByEmail("user@test.com");
        assertEquals("user@test.com", account.getEmail());
    }

    @Test void lowercasesEmailWithRootLocale() {
        Account account = savedAccount(new RegisterAccountRequest("Dineth", "USER@Test.COM", "0771234567", "Password123", AccountRole.PASSENGER));
        verify(repository).existsByEmail("user@test.com");
        assertEquals("user@test.com", account.getEmail());
    }

    @Test void trimsFullName() {
        Account account = savedAccount(new RegisterAccountRequest("  Dineth Perera  ", "dineth@example.com", "0771234567", "Password123", AccountRole.PASSENGER));
        assertEquals("Dineth Perera", account.getFullName());
    }

    @Test void trimsPhone() {
        Account account = savedAccount(new RegisterAccountRequest("Dineth", "dineth@example.com", "  0771234567  ", "Password123", AccountRole.PASSENGER));
        assertEquals("0771234567", account.getPhone());
    }

    @Test void storesBcryptHash() {
        assertTrue(savedAccount(request(AccountRole.PASSENGER)).getPasswordHash().startsWith("$2"));
    }

    @Test void storedHashIsNotPlaintext() {
        assertNotEquals("Password123", savedAccount(request(AccountRole.PASSENGER)).getPasswordHash());
    }

    @Test void bcryptHashMatchesOriginalPassword() {
        assertTrue(encoder.matches("Password123", savedAccount(request(AccountRole.PASSENGER)).getPasswordHash()));
    }

    @Test void duplicateEmailRejected() {
        when(repository.existsByEmail("dineth@example.com")).thenReturn(true);
        assertThrows(DuplicateAccountException.class, () -> service.register(request(AccountRole.PASSENGER)));
    }

    @Test void duplicateEmailWithDifferentCaseRejected() {
        when(repository.existsByEmail("user@test.com")).thenReturn(true);
        RegisterAccountRequest mixedCase = new RegisterAccountRequest("Dineth", "  User@Test.com ", "0771234567", "Password123", AccountRole.PASSENGER);
        assertThrows(DuplicateAccountException.class, () -> service.register(mixedCase));
        verify(repository).existsByEmail("user@test.com");
    }

    @Test void adminRegistrationRejected() {
        assertThrows(InvalidRegistrationRoleException.class, () -> service.register(request(AccountRole.ADMIN)));
    }

    @Test void duplicateDoesNotSave() {
        when(repository.existsByEmail("dineth@example.com")).thenReturn(true);
        assertThrows(DuplicateAccountException.class, () -> service.register(request(AccountRole.PASSENGER)));
        verify(repository, never()).save(any());
    }

    @Test void adminDoesNotSaveOrCheckDatabase() {
        assertThrows(InvalidRegistrationRoleException.class, () -> service.register(request(AccountRole.ADMIN)));
        verifyNoInteractions(repository);
    }

    @Test void responseHasNoPasswordHashProperty() {
        assertArrayEquals(new String[]{"id", "fullName", "email", "phone", "role", "status", "createdAt", "updatedAt"},
                java.util.Arrays.stream(AccountResponse.class.getRecordComponents()).map(component -> component.getName()).toArray(String[]::new));
    }

    @Test void uniqueIndexRaceIsConflict() {
        when(repository.save(any(Account.class))).thenThrow(new DuplicateKeyException("index details"));
        assertThrows(DuplicateAccountException.class, () -> service.register(request(AccountRole.PASSENGER)));
    }

    @Test void requestStringNeverLogsPlaintextPassword() {
        String description = request(AccountRole.PASSENGER).toString();
        assertFalse(description.contains("Password123"));
        assertTrue(description.contains("[REDACTED]"));
    }
}
