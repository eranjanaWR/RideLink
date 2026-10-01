package com.ridelink.farepayment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import com.ridelink.farepayment.config.FareProperties;
import com.ridelink.farepayment.dto.FinalFareRequest;
import com.ridelink.farepayment.dto.FinalFareResponse;
import com.ridelink.farepayment.exception.DuplicateFinalFareException;
import com.ridelink.farepayment.exception.FinalFareNotFoundException;
import com.ridelink.farepayment.exception.InvalidFinalFareException;
import com.ridelink.farepayment.model.FinalFare;
import com.ridelink.farepayment.repository.FinalFareRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class FinalFareService {
    private static final BigDecimal MAX_DISTANCE_KM = new BigDecimal("1000.0");
    private final FinalFareRepository repository;
    private final FareProperties properties;

    public FinalFareService(FinalFareRepository repository, FareProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public FinalFareResponse calculate(FinalFareRequest request) {
        if (request == null) {
            throw new InvalidFinalFareException("Final fare request is required");
        }
        String rideId = normalizeRideId(request.rideId());
        BigDecimal distanceKm = request.distanceKm();
        if (distanceKm == null || distanceKm.signum() <= 0
                || distanceKm.compareTo(MAX_DISTANCE_KM) > 0) {
            throw new InvalidFinalFareException("distanceKm must be greater than 0 and at most 1000");
        }
        if (repository.existsByRideId(rideId)) {
            throw new DuplicateFinalFareException();
        }
        FareCalculator.FareAmounts amounts = FareCalculator.calculate(distanceKm,
                properties.baseFare(), properties.perKmRate(), properties.minimumFare());
        FinalFare fare = new FinalFare(UUID.randomUUID().toString(), rideId, distanceKm,
                properties.baseFare(), properties.perKmRate(), amounts.distanceFare(),
                amounts.totalFare(), properties.currency(), LocalDateTime.now());
        try {
            return FinalFareResponse.from(repository.save(fare));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateFinalFareException();
        }
    }

    public FinalFareResponse getById(String finalFareId) {
        return FinalFareResponse.from(repository.findById(finalFareId)
                .orElseThrow(FinalFareNotFoundException::new));
    }

    public FinalFareResponse getByRideId(String rideId) {
        return FinalFareResponse.from(repository.findByRideId(rideId.trim())
                .orElseThrow(FinalFareNotFoundException::new));
    }

    private String normalizeRideId(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidFinalFareException("rideId is required");
        }
        String rideId = value.trim();
        if (rideId.length() > 100) {
            throw new InvalidFinalFareException("rideId must be at most 100 characters");
        }
        return rideId;
    }
}
