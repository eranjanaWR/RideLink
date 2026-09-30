package com.ridelink.account.service;

import java.util.Locale;
import java.util.UUID;

import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.dto.LoginRequest;
import com.ridelink.account.dto.LoginResponse;
import com.ridelink.account.exception.AccountDisabledException;
import com.ridelink.account.exception.AccountSuspendedException;
import com.ridelink.account.exception.InvalidCredentialsException;
import com.ridelink.account.model.Account;
import com.ridelink.account.model.AccountStatus;
import com.ridelink.account.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyPasswordHash;

    public AuthService(AccountRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Account account = repository.findByEmail(email).orElse(null);
        String hash = account == null ? dummyPasswordHash : account.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (account == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new AccountSuspendedException();
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException();
        }
        return new LoginResponse(jwtService.generateToken(account), "Bearer",
                jwtService.getExpirationSeconds(), AccountResponse.from(account));
    }
}
