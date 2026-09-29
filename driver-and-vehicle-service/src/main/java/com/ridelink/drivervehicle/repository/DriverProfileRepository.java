package com.ridelink.drivervehicle.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;
import com.ridelink.drivervehicle.model.DriverProfile;

public interface DriverProfileRepository extends MongoRepository<DriverProfile, String> {

    Optional<DriverProfile> findByAccountId(String accountId);

    boolean existsByAccountId(String accountId);

    List<DriverProfile> findByAvailabilityStatusAndServiceAreaIgnoreCase(
            DriverAvailabilityStatus availabilityStatus,
            String serviceArea);
}
