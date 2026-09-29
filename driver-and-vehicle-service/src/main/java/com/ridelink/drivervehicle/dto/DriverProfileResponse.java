package com.ridelink.drivervehicle.dto;

import java.time.LocalDateTime;

import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;

public record DriverProfileResponse(
        String id,
        String accountId,
        String licenseNumber,
        String serviceArea,
        DriverAvailabilityStatus availabilityStatus,
        Double latitude,
        Double longitude,
        LocalDateTime locationUpdatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
