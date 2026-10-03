package com.ridelink.account.service;

import java.time.LocalDateTime;

import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.dto.UpdateAccountProfileRequest;
import com.ridelink.account.exception.AccountNotFoundException;
import com.ridelink.account.model.Account;
import com.ridelink.account.model.AccountStatus;
import com.ridelink.account.repository.AccountRepository;
import org.springframework.stereotype.Service;

@Service
public class AccountManagementService {
    private final AccountRepository repository;

    public AccountManagementService(AccountRepository repository) {
        this.repository = repository;
    }

    public AccountResponse getProfile(String accountId) {
        return AccountResponse.from(findAccount(accountId));
    }

    public AccountResponse updateProfile(String accountId, UpdateAccountProfileRequest request) {
        Account current = findAccount(accountId);
        Account updated = new Account(current.getId(), request.fullName().trim(), current.getEmail(),
                request.phone().trim(), current.getPasswordHash(), current.getRole(), current.getStatus(),
                current.getCreatedAt(), LocalDateTime.now());
        return AccountResponse.from(repository.save(updated));
    }

    public AccountResponse updateStatus(String accountId, AccountStatus status) {
        Account current = findAccount(accountId);
        Account updated = new Account(current.getId(), current.getFullName(), current.getEmail(),
                current.getPhone(), current.getPasswordHash(), current.getRole(), status,
                current.getCreatedAt(), LocalDateTime.now());
        return AccountResponse.from(repository.save(updated));
    }

    private Account findAccount(String accountId) {
        return repository.findById(accountId).orElseThrow(AccountNotFoundException::new);
    }
}
