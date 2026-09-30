package com.ridelink.account.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, AccountResponse account) {
}
