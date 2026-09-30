package com.ridelink.drivervehicle.exception;

public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(String vehicleId) {
        super("Vehicle not found with id: " + vehicleId);
    }
}
