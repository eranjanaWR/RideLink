package com.ridelink.ride.exception;

public class FarePaymentServiceUnavailableException extends RuntimeException {
    public FarePaymentServiceUnavailableException() {
        super("Fare & Payment Service is currently unavailable");
    }
}
