package com.ridelink.farepayment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ridelink.farepayment.model.FareEstimate;

public record FareEstimateResponse(String id, String pickupLocation, String destinationLocation,
                                   BigDecimal distanceKm, BigDecimal baseFare, BigDecimal perKmRate,
                                   BigDecimal distanceFare, BigDecimal estimatedFare, String currency,
                                   LocalDateTime createdAt) {
    public static FareEstimateResponse from(FareEstimate estimate) {
        return new FareEstimateResponse(estimate.getId(), estimate.getPickupLocation(),
                estimate.getDestinationLocation(), estimate.getDistanceKm(), estimate.getBaseFare(),
                estimate.getPerKmRate(), estimate.getDistanceFare(), estimate.getEstimatedFare(),
                estimate.getCurrency(), estimate.getCreatedAt());
    }
}
