package com.ridelink.account.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.dto.UpdateAccountProfileRequest;
import com.ridelink.account.exception.AccountNotFoundException;
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

@ExtendWith(MockitoExtension.class)
class AccountManagementServiceTest {
    @Mock AccountRepository repository;
    private AccountManagementService service;
    private final LocalDateTime createdAt = LocalDateTime.of(2020, 1, 1, 12, 0);
    private final LocalDateTime oldUpdatedAt = LocalDateTime.of(2020, 2, 1, 12, 0);

    @BeforeEach void setUp() {
        service = new AccountManagementService(repository);
        lenient().when(repository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Account account(AccountStatus status) {
        return new Account("account-id", "Original Name", "original@example.com", "0771234567",
                "stored-bcrypt-hash", AccountRole.DRIVER, status, createdAt, oldUpdatedAt);
    }

    private void existing(AccountStatus status) {
        when(repository.findById("account-id")).thenReturn(Optional.of(account(status)));
    }

    private Account saved() {
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private UpdateAccountProfileRequest profile() {
        return new UpdateAccountProfileRequest("Updated Name", "+94 77 123 4567");
    }

    @Test void getExistingAccountSucceeds() {
        existing(AccountStatus.ACTIVE);
        AccountResponse response = service.getProfile("account-id");
        assertEquals("account-id", response.id());
        assertEquals("original@example.com", response.email());
        verify(repository, never()).save(any());
    }
    @Test void missingAccountGetThrowsNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> service.getProfile("missing"));
    }
    @Test void profileUpdateChangesFullName() {
        existing(AccountStatus.ACTIVE);
        assertEquals("Updated Name", service.updateProfile("account-id", profile()).fullName());
    }
    @Test void profileUpdateChangesPhone() {
        existing(AccountStatus.ACTIVE);
        assertEquals("+94 77 123 4567", service.updateProfile("account-id", profile()).phone());
    }
    @Test void profileUpdateTrimsFullName() {
        existing(AccountStatus.ACTIVE);
        service.updateProfile("account-id", new UpdateAccountProfileRequest("  Updated Name  ", "+94 77 123 4567"));
        assertEquals("Updated Name", saved().getFullName());
    }
    @Test void profileUpdateTrimsPhone() {
        existing(AccountStatus.ACTIVE);
        service.updateProfile("account-id", new UpdateAccountProfileRequest("Updated Name", "  +94 77 123 4567  "));
        assertEquals("+94 77 123 4567", saved().getPhone());
    }
    @Test void profileUpdatePreservesEmail() {
        existing(AccountStatus.ACTIVE); service.updateProfile("account-id", profile());
        assertEquals("original@example.com", saved().getEmail());
    }
    @Test void profileUpdatePreservesRole() {
        existing(AccountStatus.ACTIVE); service.updateProfile("account-id", profile());
        assertEquals(AccountRole.DRIVER, saved().getRole());
    }
    @Test void profileUpdatePreservesStatus() {
        existing(AccountStatus.SUSPENDED); service.updateProfile("account-id", profile());
        assertEquals(AccountStatus.SUSPENDED, saved().getStatus());
    }
    @Test void profileUpdatePreservesCreatedAt() {
        existing(AccountStatus.ACTIVE); service.updateProfile("account-id", profile());
        assertEquals(createdAt, saved().getCreatedAt());
    }
    @Test void profileUpdateChangesUpdatedAt() {
        existing(AccountStatus.ACTIVE); service.updateProfile("account-id", profile());
        assertTrue(saved().getUpdatedAt().isAfter(oldUpdatedAt));
    }
    @Test void missingAccountProfileUpdateThrowsNotFoundWithoutSave() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> service.updateProfile("missing", profile()));
        verify(repository, never()).save(any());
    }
    @Test void statusCanBecomeActive() {
        existing(AccountStatus.SUSPENDED);
        assertEquals(AccountStatus.ACTIVE, service.updateStatus("account-id", AccountStatus.ACTIVE).status());
    }
    @Test void statusCanBecomeSuspended() {
        existing(AccountStatus.ACTIVE);
        assertEquals(AccountStatus.SUSPENDED, service.updateStatus("account-id", AccountStatus.SUSPENDED).status());
    }
    @Test void statusCanBecomeDisabled() {
        existing(AccountStatus.ACTIVE);
        assertEquals(AccountStatus.DISABLED, service.updateStatus("account-id", AccountStatus.DISABLED).status());
    }
    @Test void statusUpdateChangesUpdatedAt() {
        existing(AccountStatus.ACTIVE); service.updateStatus("account-id", AccountStatus.DISABLED);
        assertTrue(saved().getUpdatedAt().isAfter(oldUpdatedAt));
    }
    @Test void missingAccountStatusUpdateThrowsNotFoundWithoutSave() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> service.updateStatus("missing", AccountStatus.DISABLED));
        verify(repository, never()).save(any());
    }
    @Test void profileUpdatePreservesPasswordHash() {
        existing(AccountStatus.ACTIVE); service.updateProfile("account-id", profile());
        assertEquals("stored-bcrypt-hash", saved().getPasswordHash());
    }
    @Test void statusUpdatePreservesPasswordHashAndRole() {
        existing(AccountStatus.ACTIVE); service.updateStatus("account-id", AccountStatus.DISABLED);
        Account saved = saved();
        assertEquals("stored-bcrypt-hash", saved.getPasswordHash());
        assertEquals(AccountRole.DRIVER, saved.getRole());
    }
    @Test void statusUpdatePreservesProfileAndCreatedAt() {
        existing(AccountStatus.ACTIVE); service.updateStatus("account-id", AccountStatus.DISABLED);
        Account saved = saved();
        assertEquals("Original Name", saved.getFullName());
        assertEquals("0771234567", saved.getPhone());
        assertEquals(createdAt, saved.getCreatedAt());
    }
    @Test void bothUpdatesPreserveId() {
        existing(AccountStatus.ACTIVE); service.updateProfile("account-id", profile());
        assertEquals("account-id", saved().getId());
    }
}
