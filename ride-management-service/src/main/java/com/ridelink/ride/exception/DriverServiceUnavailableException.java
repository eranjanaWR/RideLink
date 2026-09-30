package com.ridelink.ride.exception;

public class DriverServiceUnavailableException extends RuntimeException {

    public DriverServiceUnavailableException() {
        super("Driver & Vehicle Service is currently unavailable");
    }
}
