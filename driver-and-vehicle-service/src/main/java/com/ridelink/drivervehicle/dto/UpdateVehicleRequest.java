package com.ridelink.drivervehicle.dto;

import com.ridelink.drivervehicle.model.VehicleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateVehicleRequest(
        @NotBlank(message = "registrationNumber is required")
        @Size(max = 30, message = "registrationNumber must not exceed 30 characters")
        String registrationNumber,

        @NotBlank(message = "make is required")
        @Size(max = 50, message = "make must not exceed 50 characters")
        String make,

        @NotBlank(message = "model is required")
        @Size(max = 50, message = "model must not exceed 50 characters")
        String model,

        @NotBlank(message = "color is required")
        @Size(max = 50, message = "color must not exceed 50 characters")
        String color,

        @NotNull(message = "vehicleType is required")
        VehicleType vehicleType) {
}
