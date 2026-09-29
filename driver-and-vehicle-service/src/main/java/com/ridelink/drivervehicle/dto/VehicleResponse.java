package com.ridelink.drivervehicle.dto;

import java.time.LocalDateTime;

import com.ridelink.drivervehicle.model.VehicleType;

public record VehicleResponse(
        String id,
        String driverId,
        String registrationNumber,
        String make,
        String model,
        String color,
        VehicleType vehicleType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
