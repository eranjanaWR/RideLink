package com.ridelink.ride.exception;

public class InvalidDriverServiceResponseException extends RuntimeException {

    public InvalidDriverServiceResponseException() {
        super("Driver & Vehicle Service returned an unusable response");
    }
}
