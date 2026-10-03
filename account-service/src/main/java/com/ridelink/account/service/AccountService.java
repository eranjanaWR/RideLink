package com.ridelink.account.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.dto.RegisterAccountRequest;
import com.ridelink.account.exception.DuplicateAccountException;
import com.ridelink.account.exception.InvalidRegistrationRoleException;
import com.ridelink.account.model.Account;
import com.ridelink.account.model.AccountRole;
import com.ridelink.account.model.AccountStatus;
import com.ridelink.account.repository.AccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public AccountResponse register(RegisterAccountRequest request) {
        String fullName = request.fullName().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = request.phone().trim();

        if (request.role() != AccountRole.PASSENGER && request.role() != AccountRole.DRIVER) {
            throw new InvalidRegistrationRoleException();
        }
        if (repository.existsByEmail(email)) {
            throw new DuplicateAccountException();
        }

        LocalDateTime now = LocalDateTime.now();
        Account account = new Account(UUID.randomUUID().toString(), fullName, email, phone,
                passwordEncoder.encode(request.password()), request.role(), AccountStatus.ACTIVE, now, now);
        try {
            return AccountResponse.from(repository.save(account));
        } catch (DuplicateKeyException exception) {
            // The unique index also protects registrations that race after existsByEmail.
            throw new DuplicateAccountException();
        }
    }
}
