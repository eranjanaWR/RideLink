package com.ridelink.drivervehicle.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.ridelink.drivervehicle.dto.CreateVehicleRequest;
import com.ridelink.drivervehicle.dto.UpdateVehicleRequest;
import com.ridelink.drivervehicle.dto.VehicleResponse;
import com.ridelink.drivervehicle.exception.DriverProfileNotFoundException;
import com.ridelink.drivervehicle.exception.DuplicateVehicleRegistrationException;
import com.ridelink.drivervehicle.exception.VehicleNotFoundException;
import com.ridelink.drivervehicle.model.Vehicle;
import com.ridelink.drivervehicle.repository.DriverProfileRepository;
import com.ridelink.drivervehicle.repository.VehicleRepository;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverProfileRepository driverProfileRepository;

    public VehicleService(
            VehicleRepository vehicleRepository,
            DriverProfileRepository driverProfileRepository) {
        this.vehicleRepository = vehicleRepository;
        this.driverProfileRepository = driverProfileRepository;
    }

    public VehicleResponse create(CreateVehicleRequest request) {
        ensureDriverExists(request.driverId());

        if (vehicleRepository.existsByRegistrationNumber(request.registrationNumber())) {
            throw new DuplicateVehicleRegistrationException(request.registrationNumber());
        }

        LocalDateTime now = LocalDateTime.now();
        Vehicle vehicle = new Vehicle(
                UUID.randomUUID().toString(),
                request.driverId(),
                request.registrationNumber(),
                request.make(),
                request.model(),
                request.color(),
                request.vehicleType(),
                now,
                now);

        return save(vehicle);
    }

    public VehicleResponse getById(String vehicleId) {
        return toResponse(findVehicle(vehicleId));
    }

    public List<VehicleResponse> getByDriverId(String driverId) {
        ensureDriverExists(driverId);
        return vehicleRepository.findAllByDriverId(driverId).stream()
                .map(this::toResponse)
                .toList();
    }

    public VehicleResponse update(String vehicleId, UpdateVehicleRequest request) {
        Vehicle vehicle = findVehicle(vehicleId);

        vehicleRepository.findByRegistrationNumber(request.registrationNumber())
                .filter(existing -> !existing.getId().equals(vehicleId))
                .ifPresent(existing -> {
                    throw new DuplicateVehicleRegistrationException(request.registrationNumber());
                });

        vehicle.setRegistrationNumber(request.registrationNumber());
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setColor(request.color());
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setUpdatedAt(LocalDateTime.now());

        return save(vehicle);
    }

    public void delete(String vehicleId) {
        Vehicle vehicle = findVehicle(vehicleId);
        vehicleRepository.delete(vehicle);
    }

    private Vehicle findVehicle(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
    }

    private void ensureDriverExists(String driverId) {
        if (!driverProfileRepository.existsById(driverId)) {
            throw new DriverProfileNotFoundException(
                    "Driver profile not found with id: " + driverId);
        }
    }

    private VehicleResponse save(Vehicle vehicle) {
        try {
            return toResponse(vehicleRepository.save(vehicle));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateVehicleRegistrationException(vehicle.getRegistrationNumber());
        }
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getDriverId(),
                vehicle.getRegistrationNumber(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getColor(),
                vehicle.getVehicleType(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }
}
