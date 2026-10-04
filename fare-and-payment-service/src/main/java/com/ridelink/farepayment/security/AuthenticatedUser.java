package com.ridelink.farepayment.security;

public record AuthenticatedUser(
        String accountId,
        String email,
        AccountRole role,
        AccountStatus status
) {
}
