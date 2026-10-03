package com.ridelink.account.exception;

public class DuplicateAccountException extends RuntimeException {
    public DuplicateAccountException() {
        super("Email is already registered");
    }
}
