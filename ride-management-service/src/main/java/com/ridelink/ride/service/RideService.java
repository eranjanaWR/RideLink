package com.ridelink.ride.service;

import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.InvalidDriverServiceResponseException;
import com.ridelink.ride.exception.InvalidRideRequestException;
import com.ridelink.ride.exception.InvalidRideStateException;
import com.ridelink.ride.exception.NoEligibleDriverException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.integration.driver.DriverServiceClient;
import com.ridelink.ride.integration.driver.dto.EligibleDriverResponse;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.repository.RideRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RideService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final DriverServiceClient driverServiceClient;

    public RideService(RideRepository rideRepository, DriverServiceClient driverServiceClient) {
        this.rideRepository = rideRepository;
        this.driverServiceClient = driverServiceClient;
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

    public RideResponse assignDriver(String rideId) {
        String normalizedRideId = requireNonBlank(rideId, "rideId is required");
        Ride ride = rideRepository.findById(normalizedRideId)
                .orElseThrow(() -> new RideNotFoundException(normalizedRideId));

        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new InvalidRideStateException(ride.getId(), ride.getStatus());
        }

        List<EligibleDriverResponse> eligibleDrivers = driverServiceClient
                .getEligibleDrivers(ride.getServiceArea());
        if (eligibleDrivers == null) {
            throw new InvalidDriverServiceResponseException();
        }
        if (eligibleDrivers.isEmpty()) {
            throw new NoEligibleDriverException(ride.getServiceArea());
        }
        if (eligibleDrivers.stream().anyMatch(Objects::isNull)) {
            throw new InvalidDriverServiceResponseException();
        }

        EligibleDriverResponse selectedDriver = eligibleDrivers.stream()
                .sorted(Comparator.comparing(
                        EligibleDriverResponse::driverId,
                        Comparator.nullsFirst(String::compareTo)
                ))
                .findFirst()
                .orElseThrow(() -> new NoEligibleDriverException(ride.getServiceArea()));

        if (selectedDriver.driverId() == null || selectedDriver.driverId().isBlank()) {
            throw new InvalidDriverServiceResponseException();
        }

        String selectedDriverId = selectedDriver.driverId();
        driverServiceClient.markDriverUnavailable(selectedDriverId);

        String previousDriverId = ride.getDriverId();
        RideStatus previousStatus = ride.getStatus();
        LocalDateTime previousAssignedAt = ride.getAssignedAt();
        LocalDateTime previousUpdatedAt = ride.getUpdatedAt();

        LocalDateTime now = LocalDateTime.now();
        ride.setDriverId(selectedDriverId);
        ride.setStatus(RideStatus.ASSIGNED);
        ride.setAssignedAt(now);
        ride.setUpdatedAt(now);

        try {
            return toResponse(rideRepository.save(ride));
        } catch (RuntimeException persistenceFailure) {
            ride.setDriverId(previousDriverId);
            ride.setStatus(previousStatus);
            ride.setAssignedAt(previousAssignedAt);
            ride.setUpdatedAt(previousUpdatedAt);
            compensateFailedAssignment(selectedDriverId, ride.getId());
            throw persistenceFailure;
        }
    }

    public RideResponse acceptRide(String rideId) {
        Ride ride = findRide(rideId);
        if (ride.getStatus() != RideStatus.ASSIGNED) {
            throw new InvalidRideStateException("Ride must be ASSIGNED before it can be accepted");
        }
        if (ride.getDriverId() == null || ride.getDriverId().isBlank()) {
            throw new InvalidRideStateException("Ride must have an assigned driver before it can be accepted");
        }

        LocalDateTime now = LocalDateTime.now();
        ride.setStatus(RideStatus.ACCEPTED);
        ride.setAcceptedAt(now);
        ride.setUpdatedAt(now);
        return toResponse(rideRepository.save(ride));
    }

    public RideResponse startRide(String rideId) {
        Ride ride = findRide(rideId);
        if (ride.getStatus() != RideStatus.ACCEPTED) {
            throw new InvalidRideStateException("Ride must be ACCEPTED before it can be started");
        }

        LocalDateTime now = LocalDateTime.now();
        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(now);
        ride.setUpdatedAt(now);
        return toResponse(rideRepository.save(ride));
    }

    public RideResponse completeRide(String rideId) {
        Ride ride = findRide(rideId);
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new InvalidRideStateException("Ride must be IN_PROGRESS before it can be completed");
        }
        if (ride.getDriverId() == null || ride.getDriverId().isBlank()) {
            throw new InvalidRideStateException("Ride must have an assigned driver before it can be completed");
        }

        driverServiceClient.markDriverAvailable(ride.getDriverId());

        LocalDateTime now = LocalDateTime.now();
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(now);
        ride.setUpdatedAt(now);
        return toResponse(rideRepository.save(ride));
    }

    public RideResponse cancelRide(String rideId) {
        Ride ride = findRide(rideId);
        if (ride.getStatus() != RideStatus.REQUESTED
                && ride.getStatus() != RideStatus.ASSIGNED
                && ride.getStatus() != RideStatus.ACCEPTED) {
            throw new InvalidRideStateException(
                    "Ride cannot be cancelled from status " + ride.getStatus()
            );
        }

        if (ride.getStatus() == RideStatus.ASSIGNED || ride.getStatus() == RideStatus.ACCEPTED) {
            if (ride.getDriverId() == null || ride.getDriverId().isBlank()) {
                throw new InvalidRideStateException("Ride must have an assigned driver before it can be cancelled");
            }
            driverServiceClient.markDriverAvailable(ride.getDriverId());
        }

        LocalDateTime now = LocalDateTime.now();
        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(now);
        ride.setUpdatedAt(now);
        return toResponse(rideRepository.save(ride));
    }

    private void compensateFailedAssignment(String driverId, String rideId) {
        try {
            driverServiceClient.markDriverAvailable(driverId);
        } catch (RuntimeException compensationFailure) {
            LOGGER.warn(
                    "Could not restore availability for driver {} after assignment persistence failed for ride {}",
                    driverId,
                    rideId
            );
        }
    }

    private Ride findRide(String rideId) {
        String normalizedRideId = requireNonBlank(rideId, "rideId is required");
        return rideRepository.findById(normalizedRideId)
                .orElseThrow(() -> new RideNotFoundException(normalizedRideId));
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
