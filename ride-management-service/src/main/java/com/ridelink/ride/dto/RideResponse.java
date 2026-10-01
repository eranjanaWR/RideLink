package com.ridelink.ride.dto;

import com.ridelink.ride.model.RideStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RideResponse(
        String id,
        String passengerId,
        String pickupLocation,
        String destinationLocation,
        String serviceArea,
        String driverId,
        RideStatus status,
        BigDecimal estimatedFare,
        BigDecimal finalFare,
        String paymentId,
        LocalDateTime requestedAt,
        LocalDateTime assignedAt,
        LocalDateTime acceptedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        LocalDateTime updatedAt
) {
}
