package com.ridelink.ride.exception;

import com.ridelink.ride.model.RideStatus;

public class InvalidRideStateException extends RuntimeException {

    public InvalidRideStateException(String message) {
        super(message);
    }

    public InvalidRideStateException(String rideId, RideStatus status) {
        super("Ride " + rideId + " cannot be assigned while its status is " + status);
    }
}
