package com.ridelink.ride.integration.farepayment.dto;

import java.math.BigDecimal;

public record FinalFareRequest(String rideId, BigDecimal distanceKm) {
}
