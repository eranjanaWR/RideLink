package com.ridelink.ride.integration.driver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DriverAvailabilityResponse(
        String id,
        DriverAvailabilityStatus availabilityStatus
) {
}
