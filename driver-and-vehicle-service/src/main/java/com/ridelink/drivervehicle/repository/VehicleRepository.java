package com.ridelink.drivervehicle.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ridelink.drivervehicle.model.Vehicle;

public interface VehicleRepository extends MongoRepository<Vehicle, String> {

    List<Vehicle> findAllByDriverId(String driverId);

    boolean existsByRegistrationNumber(String registrationNumber);

    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);
}
