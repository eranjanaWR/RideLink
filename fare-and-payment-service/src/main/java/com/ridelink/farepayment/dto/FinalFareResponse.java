package com.ridelink.farepayment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.ridelink.farepayment.model.FinalFare;

public record FinalFareResponse(String id, String rideId, BigDecimal distanceKm,
                                BigDecimal baseFare, BigDecimal perKmRate,
                                BigDecimal distanceFare, BigDecimal finalFare,
                                String currency, LocalDateTime createdAt) {
    public static FinalFareResponse from(FinalFare fare) {
        return new FinalFareResponse(fare.getId(), fare.getRideId(), fare.getDistanceKm(),
                fare.getBaseFare(), fare.getPerKmRate(), fare.getDistanceFare(),
                fare.getFinalFare(), fare.getCurrency(), fare.getCreatedAt());
    }
}
