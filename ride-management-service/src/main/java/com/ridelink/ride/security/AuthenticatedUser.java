package com.ridelink.ride.security;

public record AuthenticatedUser(
        String accountId,
        String email,
        AccountRole role,
        AccountStatus status
) {
}
