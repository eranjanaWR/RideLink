package com.ridelink.farepayment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FareEstimateRequest(
        @NotBlank @Size(max = 150) String pickupLocation,
        @NotBlank @Size(max = 150) String destinationLocation,
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("1000.0") BigDecimal distanceKm) {
    public FareEstimateRequest {
        pickupLocation = pickupLocation == null ? null : pickupLocation.trim();
        destinationLocation = destinationLocation == null ? null : destinationLocation.trim();
    }
}
