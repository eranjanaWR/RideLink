package com.ridelink.ride.exception;

public class NoEligibleDriverException extends RuntimeException {

    public NoEligibleDriverException(String serviceArea) {
        super("No eligible driver is currently available for service area " + serviceArea);
    }
}
