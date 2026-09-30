package com.ridelink.ride.integration.driver.dto;

public record EligibleDriverResponse(
        String driverId,
        String accountId,
        String serviceArea,
        Double latitude,
        Double longitude,
        String availabilityStatus,
        String vehicleId,
        String registrationNumber,
        String vehicleType
) {
}
