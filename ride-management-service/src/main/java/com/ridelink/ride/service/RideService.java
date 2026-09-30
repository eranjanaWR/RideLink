package com.ridelink.ride.service;

import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.InvalidRideRequestException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.repository.RideRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RideService {

    private final RideRepository rideRepository;

    public RideService(RideRepository rideRepository) {
        this.rideRepository = rideRepository;
    }

    public RideResponse createRide(CreateRideRequest request) {
        String passengerId = request.passengerId().trim();
        String pickupLocation = request.pickupLocation().trim();
        String destinationLocation = request.destinationLocation().trim();
        String serviceArea = request.serviceArea().trim();

        if (pickupLocation.equalsIgnoreCase(destinationLocation)) {
            throw new InvalidRideRequestException("pickupLocation and destinationLocation must be different");
        }

        LocalDateTime now = LocalDateTime.now();
        Ride ride = new Ride();
        ride.setId(UUID.randomUUID().toString());
        ride.setPassengerId(passengerId);
        ride.setPickupLocation(pickupLocation);
        ride.setDestinationLocation(destinationLocation);
        ride.setServiceArea(serviceArea);
        ride.setDriverId(null);
        ride.setStatus(RideStatus.REQUESTED);
        ride.setEstimatedFare(null);
        ride.setFinalFare(null);
        ride.setRequestedAt(now);
        ride.setAssignedAt(null);
        ride.setAcceptedAt(null);
        ride.setStartedAt(null);
        ride.setCompletedAt(null);
        ride.setCancelledAt(null);
        ride.setUpdatedAt(now);

        return toResponse(rideRepository.save(ride));
    }

    public RideResponse getRideById(String rideId) {
        String normalizedRideId = requireNonBlank(rideId, "rideId is required");
        Ride ride = rideRepository.findById(normalizedRideId)
                .orElseThrow(() -> new RideNotFoundException(normalizedRideId));
        return toResponse(ride);
    }

    public List<RideResponse> getRidesByPassengerId(String passengerId) {
        String normalizedPassengerId = requireNonBlank(passengerId, "passengerId is required");
        return rideRepository.findAllByPassengerId(normalizedPassengerId).stream()
                .map(this::toResponse)
                .toList();
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidRideRequestException(message);
        }
        return value.trim();
    }

    private RideResponse toResponse(Ride ride) {
        return new RideResponse(
                ride.getId(),
                ride.getPassengerId(),
                ride.getPickupLocation(),
                ride.getDestinationLocation(),
                ride.getServiceArea(),
                ride.getDriverId(),
                ride.getStatus(),
                ride.getEstimatedFare(),
                ride.getFinalFare(),
                ride.getRequestedAt(),
                ride.getAssignedAt(),
                ride.getAcceptedAt(),
                ride.getStartedAt(),
                ride.getCompletedAt(),
                ride.getCancelledAt(),
                ride.getUpdatedAt()
        );
    }
}
