package com.ridelink.drivervehicle.exception;

public class DuplicateDriverProfileException extends RuntimeException {

    public DuplicateDriverProfileException(String accountId) {
        super("A driver profile already exists for accountId: " + accountId);
    }
}
