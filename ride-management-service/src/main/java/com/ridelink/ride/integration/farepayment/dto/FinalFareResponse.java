package com.ridelink.ride.integration.farepayment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinalFareResponse(
        String id,
        String rideId,
        BigDecimal distanceKm,
        BigDecimal finalFare,
        String currency
) {
}
