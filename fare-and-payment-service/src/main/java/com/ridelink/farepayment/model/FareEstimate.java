package com.ridelink.farepayment.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "fare_estimates")
public class FareEstimate {
    @Id
    private final String id;
    private final String pickupLocation;
    private final String destinationLocation;
    private final BigDecimal distanceKm;
    private final BigDecimal baseFare;
    private final BigDecimal perKmRate;
    private final BigDecimal distanceFare;
    private final BigDecimal estimatedFare;
    private final String currency;
    private final LocalDateTime createdAt;

    public FareEstimate(String id, String pickupLocation, String destinationLocation,
                        BigDecimal distanceKm, BigDecimal baseFare, BigDecimal perKmRate,
                        BigDecimal distanceFare, BigDecimal estimatedFare, String currency,
                        LocalDateTime createdAt) {
        this.id = id;
        this.pickupLocation = pickupLocation;
        this.destinationLocation = destinationLocation;
        this.distanceKm = distanceKm;
        this.baseFare = baseFare;
        this.perKmRate = perKmRate;
        this.distanceFare = distanceFare;
        this.estimatedFare = estimatedFare;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getPickupLocation() { return pickupLocation; }
    public String getDestinationLocation() { return destinationLocation; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public BigDecimal getBaseFare() { return baseFare; }
    public BigDecimal getPerKmRate() { return perKmRate; }
    public BigDecimal getDistanceFare() { return distanceFare; }
    public BigDecimal getEstimatedFare() { return estimatedFare; }
    public String getCurrency() { return currency; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
