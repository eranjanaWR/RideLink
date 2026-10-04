package com.ridelink.farepayment.security;

import com.ridelink.farepayment.dto.PaymentResponse;
import com.ridelink.farepayment.service.PaymentService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class FarePaymentAuthorizationService {

    private final PaymentService paymentService;

    public FarePaymentAuthorizationService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void requireAdminOrInternal(Authentication authentication) {
        if (isInternal(authentication)) {
            return;
        }
        AuthenticatedUser user = user(authentication);
        if (user.role() == AccountRole.ADMIN) {
            return;
        }
        deny();
    }

    public void requirePaymentAccessById(Authentication authentication, String paymentId) {
        if (isInternalOrAdmin(authentication)) {
            return;
        }
        AuthenticatedUser user = user(authentication);
        if (user.role() != AccountRole.PASSENGER) {
            deny();
        }
        requireOwner(user, paymentService.getPayment(paymentId));
    }

    public void requirePaymentAccessByRide(Authentication authentication, String rideId) {
        if (isInternalOrAdmin(authentication)) {
            return;
        }
        AuthenticatedUser user = user(authentication);
        if (user.role() != AccountRole.PASSENGER) {
            deny();
        }
        requireOwner(user, paymentService.getPaymentByRide(rideId));
    }

    private void requireOwner(AuthenticatedUser user, PaymentResponse payment) {
        if (user.accountId().equals(payment.passengerId())) {
            return;
        }
        deny();
    }

    private boolean isInternalOrAdmin(Authentication authentication) {
        if (isInternal(authentication)) {
            return true;
        }
        return user(authentication).role() == AccountRole.ADMIN;
    }

    private boolean isInternal(Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof InternalServicePrincipal;
    }

    private AuthenticatedUser user(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        deny();
        return null;
    }

    private void deny() {
        throw new AccessDeniedException("Access denied");
    }
}
