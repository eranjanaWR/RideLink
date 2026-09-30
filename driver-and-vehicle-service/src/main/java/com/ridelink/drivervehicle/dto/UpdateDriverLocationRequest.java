package com.ridelink.drivervehicle.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UpdateDriverLocationRequest(
        @NotNull(message = "latitude is required")
        @DecimalMin(value = "-90.0", message = "latitude must be at least -90")
        @DecimalMax(value = "90.0", message = "latitude must not exceed 90")
        Double latitude,

        @NotNull(message = "longitude is required")
        @DecimalMin(value = "-180.0", message = "longitude must be at least -180")
        @DecimalMax(value = "180.0", message = "longitude must not exceed 180")
        Double longitude) {
}
