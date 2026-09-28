package com.ridelink.drivervehicle.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.ridelink.drivervehicle.dto.CreateDriverProfileRequest;
import com.ridelink.drivervehicle.dto.DriverProfileResponse;
import com.ridelink.drivervehicle.dto.UpdateDriverProfileRequest;
import com.ridelink.drivervehicle.exception.DriverProfileNotFoundException;
import com.ridelink.drivervehicle.exception.DuplicateDriverProfileException;
import com.ridelink.drivervehicle.model.DriverProfile;
import com.ridelink.drivervehicle.repository.DriverProfileRepository;

@Service
public class DriverProfileService {

    private final DriverProfileRepository repository;

    public DriverProfileService(DriverProfileRepository repository) {
        this.repository = repository;
    }

    public DriverProfileResponse create(CreateDriverProfileRequest request) {
        if (repository.existsByAccountId(request.accountId())) {
            throw new DuplicateDriverProfileException(request.accountId());
        }

        LocalDateTime now = LocalDateTime.now();
        DriverProfile profile = new DriverProfile(
                UUID.randomUUID().toString(),
                request.accountId(),
                request.licenseNumber(),
                request.serviceArea(),
                now,
                now);

        try {
            return toResponse(repository.save(profile));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateDriverProfileException(request.accountId());
        }
    }

    public DriverProfileResponse getById(String driverId) {
        return repository.findById(driverId)
                .map(this::toResponse)
                .orElseThrow(() -> new DriverProfileNotFoundException(
                        "Driver profile not found with id: " + driverId));
    }

    public DriverProfileResponse getByAccountId(String accountId) {
        return repository.findByAccountId(accountId)
                .map(this::toResponse)
                .orElseThrow(() -> new DriverProfileNotFoundException(
                        "Driver profile not found for accountId: " + accountId));
    }

    public DriverProfileResponse update(String driverId, UpdateDriverProfileRequest request) {
        DriverProfile profile = repository.findById(driverId)
                .orElseThrow(() -> new DriverProfileNotFoundException(
                        "Driver profile not found with id: " + driverId));

        profile.setLicenseNumber(request.licenseNumber());
        profile.setServiceArea(request.serviceArea());
        profile.setUpdatedAt(LocalDateTime.now());

        return toResponse(repository.save(profile));
    }

    private DriverProfileResponse toResponse(DriverProfile profile) {
        return new DriverProfileResponse(
                profile.getId(),
                profile.getAccountId(),
                profile.getLicenseNumber(),
                profile.getServiceArea(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
