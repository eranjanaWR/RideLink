package com.ridelink.farepayment.repository;

import java.util.Optional;

import com.ridelink.farepayment.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRepository extends MongoRepository<Payment, String> {
    Optional<Payment> findByRideId(String rideId);
}
