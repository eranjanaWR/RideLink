package com.ridelink.ride.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRideRequest(
        @NotBlank(message = "passengerId is required")
        @Size(max = 100, message = "passengerId must not exceed 100 characters")
        String passengerId,

        @NotBlank(message = "pickupLocation is required")
        @Size(max = 255, message = "pickupLocation must not exceed 255 characters")
        String pickupLocation,

        @NotBlank(message = "destinationLocation is required")
        @Size(max = 255, message = "destinationLocation must not exceed 255 characters")
        String destinationLocation,

        @NotBlank(message = "serviceArea is required")
        @Size(max = 100, message = "serviceArea must not exceed 100 characters")
        String serviceArea
) {
}
