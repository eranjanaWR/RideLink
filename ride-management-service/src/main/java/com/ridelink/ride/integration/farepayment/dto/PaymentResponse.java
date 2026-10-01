package com.ridelink.ride.integration.farepayment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResponse(
        String id,
        String rideId,
        String passengerId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status
) {
}
