package com.ridelink.farepayment.repository;

import java.util.Optional;
import com.ridelink.farepayment.model.FinalFare;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FinalFareRepository extends MongoRepository<FinalFare, String> {
    Optional<FinalFare> findByRideId(String rideId);
    boolean existsByRideId(String rideId);
}
