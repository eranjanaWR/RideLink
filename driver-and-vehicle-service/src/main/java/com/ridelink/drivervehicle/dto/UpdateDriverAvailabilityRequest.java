package com.ridelink.drivervehicle.dto;

import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateDriverAvailabilityRequest(
        @NotNull(message = "availabilityStatus is required")
        DriverAvailabilityStatus availabilityStatus) {
}
