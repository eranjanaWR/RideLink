package com.ridelink.drivervehicle.dto;

import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;
import com.ridelink.drivervehicle.model.VehicleType;

public record EligibleDriverResponse(
        String driverId,
        String accountId,
        String serviceArea,
        Double latitude,
        Double longitude,
        DriverAvailabilityStatus availabilityStatus,
        String vehicleId,
        String registrationNumber,
        VehicleType vehicleType) {
}
