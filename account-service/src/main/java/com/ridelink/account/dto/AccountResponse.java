package com.ridelink.account.dto;

import java.time.LocalDateTime;

import com.ridelink.account.model.Account;
import com.ridelink.account.model.AccountRole;
import com.ridelink.account.model.AccountStatus;

public record AccountResponse(String id, String fullName, String email, String phone,
                              AccountRole role, AccountStatus status,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getFullName(), account.getEmail(),
                account.getPhone(), account.getRole(), account.getStatus(),
                account.getCreatedAt(), account.getUpdatedAt());
    }
}
