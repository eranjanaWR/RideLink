package com.ridelink.ride.exception;

public class InvalidFarePaymentResponseException extends RuntimeException {
    public InvalidFarePaymentResponseException() {
        super("Fare & Payment Service returned an unusable response");
    }
}
