package com.ridelink.ride.repository;

import com.ridelink.ride.model.Ride;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RideRepository extends MongoRepository<Ride, String> {

    List<Ride> findAllByPassengerId(String passengerId);
}
