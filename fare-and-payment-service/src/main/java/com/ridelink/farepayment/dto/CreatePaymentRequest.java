package com.ridelink.farepayment.dto;

import java.math.BigDecimal;

import com.ridelink.farepayment.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @NotBlank @Size(max = 100) String rideId,
        @NotBlank @Size(max = 100) String passengerId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false)
        @DecimalMax("1000000.00") BigDecimal amount,
        @NotNull PaymentMethod method) {
    public CreatePaymentRequest {
        rideId = rideId == null ? null : rideId.trim();
        passengerId = passengerId == null ? null : passengerId.trim();
    }
}
