package com.ridelink.ride.dto;

import com.ridelink.ride.integration.farepayment.dto.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CompleteRideWithPaymentRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("1000.0") BigDecimal distanceKm,
        @NotNull PaymentMethod paymentMethod
) {
}
