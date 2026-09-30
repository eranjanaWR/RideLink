package com.ridelink.drivervehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDriverProfileRequest(
        @NotBlank(message = "accountId is required")
        @Size(max = 100, message = "accountId must not exceed 100 characters")
        String accountId,

        @NotBlank(message = "licenseNumber is required")
        @Size(max = 50, message = "licenseNumber must not exceed 50 characters")
        String licenseNumber,

        @NotBlank(message = "serviceArea is required")
        @Size(max = 100, message = "serviceArea must not exceed 100 characters")
        String serviceArea) {
}
