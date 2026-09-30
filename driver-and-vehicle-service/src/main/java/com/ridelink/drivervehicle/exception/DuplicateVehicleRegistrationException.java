package com.ridelink.drivervehicle.exception;

public class DuplicateVehicleRegistrationException extends RuntimeException {

    public DuplicateVehicleRegistrationException(String registrationNumber) {
        super("A vehicle already exists with registrationNumber: " + registrationNumber);
    }
}
