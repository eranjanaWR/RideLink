package com.ridelink.ride.exception;

public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException() {
        super("Existing payment conflicts with the requested ride completion");
    }
}
