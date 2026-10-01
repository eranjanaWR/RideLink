package com.ridelink.farepayment.exception;

public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException(String rideId) {
        super("A payment already exists for ride " + rideId);
    }
}
