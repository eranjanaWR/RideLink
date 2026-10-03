package com.ridelink.farepayment.config;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fare")
public record FareProperties(String currency, BigDecimal baseFare, BigDecimal perKmRate,
                             BigDecimal minimumFare) {
    public FareProperties {
        if (currency == null || currency.isBlank() || baseFare == null || perKmRate == null
                || minimumFare == null || baseFare.signum() < 0 || perKmRate.signum() < 0
                || minimumFare.signum() < 0) {
            throw new IllegalArgumentException("Fare currency and non-negative rates are required");
        }
        currency = currency.trim();
        baseFare = baseFare.setScale(2, RoundingMode.HALF_UP);
        perKmRate = perKmRate.setScale(2, RoundingMode.HALF_UP);
        minimumFare = minimumFare.setScale(2, RoundingMode.HALF_UP);
    }
}
