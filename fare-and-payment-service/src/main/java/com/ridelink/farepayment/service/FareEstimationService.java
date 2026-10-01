package com.ridelink.farepayment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ridelink.farepayment.config.FareProperties;
import com.ridelink.farepayment.dto.FareEstimateRequest;
import com.ridelink.farepayment.dto.FareEstimateResponse;
import com.ridelink.farepayment.exception.FareEstimateNotFoundException;
import com.ridelink.farepayment.exception.InvalidFareEstimateException;
import com.ridelink.farepayment.model.FareEstimate;
import com.ridelink.farepayment.repository.FareEstimateRepository;
import org.springframework.stereotype.Service;

@Service
public class FareEstimationService {
    private static final BigDecimal MAX_DISTANCE_KM = new BigDecimal("1000.0");

    private final FareEstimateRepository repository;
    private final FareProperties properties;

    public FareEstimationService(FareEstimateRepository repository, FareProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public FareEstimateResponse estimate(FareEstimateRequest request) {
        if (request == null) {
            throw new InvalidFareEstimateException("Fare estimate request is required");
        }
        String pickup = normalizeLocation(request.pickupLocation(), "pickupLocation");
        String destination = normalizeLocation(request.destinationLocation(), "destinationLocation");
        BigDecimal distanceKm = request.distanceKm();
        if (distanceKm == null || distanceKm.signum() <= 0
                || distanceKm.compareTo(MAX_DISTANCE_KM) > 0) {
            throw new InvalidFareEstimateException("distanceKm must be greater than 0 and at most 1000");
        }
        if (pickup.equalsIgnoreCase(destination)) {
            throw new InvalidFareEstimateException("Pickup and destination must be different");
        }

        BigDecimal distanceFare = distanceKm.multiply(properties.perKmRate())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal estimatedFare = properties.baseFare().add(distanceFare)
                .max(properties.minimumFare()).setScale(2, RoundingMode.HALF_UP);
        FareEstimate estimate = new FareEstimate(UUID.randomUUID().toString(), pickup, destination,
                distanceKm, properties.baseFare(), properties.perKmRate(), distanceFare,
                estimatedFare, properties.currency(), LocalDateTime.now());
        return FareEstimateResponse.from(repository.save(estimate));
    }

    public FareEstimateResponse getEstimate(String estimateId) {
        return FareEstimateResponse.from(repository.findById(estimateId)
                .orElseThrow(FareEstimateNotFoundException::new));
    }

    private String normalizeLocation(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidFareEstimateException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 150) {
            throw new InvalidFareEstimateException(fieldName + " must be at most 150 characters");
        }
        return normalized;
    }
}
