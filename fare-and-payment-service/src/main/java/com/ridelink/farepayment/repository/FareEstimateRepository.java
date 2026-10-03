package com.ridelink.farepayment.repository;

import com.ridelink.farepayment.model.FareEstimate;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FareEstimateRepository extends MongoRepository<FareEstimate, String> {
}
