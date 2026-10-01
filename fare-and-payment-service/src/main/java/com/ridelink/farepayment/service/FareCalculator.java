package com.ridelink.farepayment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FareCalculator {
    private FareCalculator() {
    }

    public static FareAmounts calculate(BigDecimal distanceKm, BigDecimal baseFare,
                                        BigDecimal perKmRate, BigDecimal minimumFare) {
        BigDecimal distanceFare = distanceKm.multiply(perKmRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalFare = baseFare.add(distanceFare).max(minimumFare)
                .setScale(2, RoundingMode.HALF_UP);
        return new FareAmounts(distanceFare, totalFare);
    }

    public record FareAmounts(BigDecimal distanceFare, BigDecimal totalFare) {
    }
}
