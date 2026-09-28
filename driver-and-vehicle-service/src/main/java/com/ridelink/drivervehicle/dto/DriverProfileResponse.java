package com.ridelink.drivervehicle.dto;

import java.time.LocalDateTime;

public record DriverProfileResponse(
        String id,
        String accountId,
        String licenseNumber,
        String serviceArea,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
