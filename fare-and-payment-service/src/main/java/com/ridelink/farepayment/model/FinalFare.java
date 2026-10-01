package com.ridelink.farepayment.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "final_fares")
public class FinalFare {
    @Id
    private final String id;
    @Indexed(unique = true)
    private final String rideId;
    private final BigDecimal distanceKm;
    private final BigDecimal baseFare;
    private final BigDecimal perKmRate;
    private final BigDecimal distanceFare;
    private final BigDecimal finalFare;
    private final String currency;
    private final LocalDateTime createdAt;

    public FinalFare(String id, String rideId, BigDecimal distanceKm, BigDecimal baseFare,
                     BigDecimal perKmRate, BigDecimal distanceFare, BigDecimal finalFare,
                     String currency, LocalDateTime createdAt) {
        this.id = id;
        this.rideId = rideId;
        this.distanceKm = distanceKm;
        this.baseFare = baseFare;
        this.perKmRate = perKmRate;
        this.distanceFare = distanceFare;
        this.finalFare = finalFare;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getRideId() { return rideId; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public BigDecimal getBaseFare() { return baseFare; }
    public BigDecimal getPerKmRate() { return perKmRate; }
    public BigDecimal getDistanceFare() { return distanceFare; }
    public BigDecimal getFinalFare() { return finalFare; }
    public String getCurrency() { return currency; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
