package com.ridelink.farepayment.exception;

public class DuplicateFinalFareException extends RuntimeException {
    public DuplicateFinalFareException() {
        super("A final fare already exists for this ride");
    }
}
