package com.ridelink.ride.integration.farepayment.dto;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        String rideId,
        String passengerId,
        BigDecimal amount,
        PaymentMethod method
) {
}
