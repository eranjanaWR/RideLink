package com.ridelink.farepayment.exception;

public class FareEstimateNotFoundException extends RuntimeException {
    public FareEstimateNotFoundException() {
        super("Fare estimate not found");
    }
}
