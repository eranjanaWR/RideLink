package com.ridelink.account.exception;

public class InvalidRegistrationRoleException extends RuntimeException {
    public InvalidRegistrationRoleException() {
        super("Public registration supports PASSENGER and DRIVER only");
    }
}
