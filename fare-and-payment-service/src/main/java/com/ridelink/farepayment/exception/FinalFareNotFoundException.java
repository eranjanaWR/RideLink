package com.ridelink.farepayment.exception;

public class FinalFareNotFoundException extends RuntimeException {
    public FinalFareNotFoundException() {
        super("Final fare not found");
    }
}
