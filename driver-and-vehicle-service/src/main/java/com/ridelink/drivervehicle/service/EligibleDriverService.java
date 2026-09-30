package com.ridelink.drivervehicle.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ridelink.drivervehicle.dto.EligibleDriverResponse;
import com.ridelink.drivervehicle.exception.InvalidServiceAreaException;
import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;
import com.ridelink.drivervehicle.model.DriverProfile;
import com.ridelink.drivervehicle.model.Vehicle;
import com.ridelink.drivervehicle.repository.DriverProfileRepository;
import com.ridelink.drivervehicle.repository.VehicleRepository;

@Service
public class EligibleDriverService {

    private static final int MAX_SERVICE_AREA_LENGTH = 100;

    private final DriverProfileRepository driverProfileRepository;
    private final VehicleRepository vehicleRepository;

    public EligibleDriverService(
            DriverProfileRepository driverProfileRepository,
            VehicleRepository vehicleRepository) {
        this.driverProfileRepository = driverProfileRepository;
        this.vehicleRepository = vehicleRepository;
    }

    public List<EligibleDriverResponse> findEligibleDrivers(String serviceArea) {
        String normalizedServiceArea = normalizeServiceArea(serviceArea);

        return driverProfileRepository
                .findByAvailabilityStatusAndServiceAreaIgnoreCase(
                        DriverAvailabilityStatus.AVAILABLE,
                        normalizedServiceArea)
                .stream()
                .filter(profile -> isEligibleCandidate(profile, normalizedServiceArea))
                .map(this::toEligibleResponse)
                .flatMap(List::stream)
                .toList();
    }

    private String normalizeServiceArea(String serviceArea) {
        if (serviceArea == null || serviceArea.isBlank()) {
            throw new InvalidServiceAreaException("serviceArea is required and must not be blank");
        }

        String normalized = serviceArea.trim();
        if (normalized.length() > MAX_SERVICE_AREA_LENGTH) {
            throw new InvalidServiceAreaException(
                    "serviceArea must not exceed " + MAX_SERVICE_AREA_LENGTH + " characters");
        }
        return normalized;
    }

    private boolean isEligibleCandidate(DriverProfile profile, String normalizedServiceArea) {
        return profile.getAvailabilityStatus() == DriverAvailabilityStatus.AVAILABLE
                && profile.getServiceArea() != null
                && profile.getServiceArea().trim().equalsIgnoreCase(normalizedServiceArea)
                && profile.getLatitude() != null
                && profile.getLongitude() != null;
    }

    private List<EligibleDriverResponse> toEligibleResponse(DriverProfile profile) {
        return vehicleRepository.findAllByDriverId(profile.getId()).stream()
                .findFirst()
                .map(vehicle -> List.of(toResponse(profile, vehicle)))
                .orElseGet(List::of);
    }

    private EligibleDriverResponse toResponse(DriverProfile profile, Vehicle vehicle) {
        return new EligibleDriverResponse(
                profile.getId(),
                profile.getAccountId(),
                profile.getServiceArea(),
                profile.getLatitude(),
                profile.getLongitude(),
                profile.getAvailabilityStatus(),
                vehicle.getId(),
                vehicle.getRegistrationNumber(),
                vehicle.getVehicleType());
    }
}
