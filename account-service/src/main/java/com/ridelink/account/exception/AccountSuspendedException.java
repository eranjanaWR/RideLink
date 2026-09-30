package com.ridelink.account.exception;

public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException() {
        super("Account is suspended");
    }
}
