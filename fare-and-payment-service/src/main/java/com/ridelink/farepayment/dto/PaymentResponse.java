package com.ridelink.farepayment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ridelink.farepayment.model.Payment;
import com.ridelink.farepayment.model.PaymentMethod;
import com.ridelink.farepayment.model.PaymentStatus;

public record PaymentResponse(
        String id,
        String rideId,
        String passengerId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getRideId(), payment.getPassengerId(),
                payment.getAmount(), payment.getCurrency(), payment.getMethod(), payment.getStatus(),
                payment.getCreatedAt(), payment.getUpdatedAt(), payment.getCompletedAt());
    }
}
