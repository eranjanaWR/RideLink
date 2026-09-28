package com.ridelink.drivervehicle.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ridelink.drivervehicle.model.DriverProfile;

public interface DriverProfileRepository extends MongoRepository<DriverProfile, String> {

    Optional<DriverProfile> findByAccountId(String accountId);

    boolean existsByAccountId(String accountId);
}
