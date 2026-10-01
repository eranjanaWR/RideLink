package com.ridelink.farepayment.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FinalFareRequest(
        @NotBlank @Size(max = 100) String rideId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("1000.0") BigDecimal distanceKm) {
    public FinalFareRequest {
        rideId = rideId == null ? null : rideId.trim();
    }
}
