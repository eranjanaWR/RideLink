package com.ridelink.drivervehicle.dto;

import java.time.LocalDateTime;

import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;

public record DriverProfileResponse(
        String id,
        String accountId,
        String licenseNumber,
        String serviceArea,
        DriverAvailabilityStatus availabilityStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
