package com.ridelink.drivervehicle.security;

public record AuthenticatedUser(
        String accountId,
        String email,
        AccountRole role,
        AccountStatus status) {
}
