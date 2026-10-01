package com.ridelink.farepayment.exception;

import com.ridelink.farepayment.model.PaymentStatus;

public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(PaymentStatus status, String action) {
        super("Payment cannot be " + action + " while its status is " + status);
    }
}
