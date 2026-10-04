package com.ridelink.drivervehicle.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.ridelink.drivervehicle.service.DriverProfileService;
import com.ridelink.drivervehicle.service.VehicleService;

@Component
public class AuthorizationService {

    private final DriverProfileService driverProfileService;
    private final VehicleService vehicleService;

    public AuthorizationService(
            DriverProfileService driverProfileService,
            VehicleService vehicleService) {
        this.driverProfileService = driverProfileService;
        this.vehicleService = vehicleService;
    }

    public void requireDriverOrAdminForAccount(
            AuthenticatedUser user,
            String accountId) {
        if (isAdmin(user)
                || (user.role() == AccountRole.DRIVER
                && user.accountId().equals(accountId))) {
            return;
        }
        deny();
    }

    public void requireAccountOwnerOrAdmin(
            AuthenticatedUser user,
            String accountId) {
        if (isAdmin(user) || user.accountId().equals(accountId)) {
            return;
        }
        deny();
    }

    public void requireDriverProfileOwnerOrAdmin(
            AuthenticatedUser user,
            String driverId) {
        String ownerAccountId = driverProfileService.getById(driverId).accountId();
        if (isAdmin(user)
                || (user.role() == AccountRole.DRIVER
                && user.accountId().equals(ownerAccountId))) {
            return;
        }
        deny();
    }

    public void requireDriverOwnerAdminOrInternal(
            Authentication authentication,
            String driverId) {
        if (authentication.getPrincipal() instanceof InternalServicePrincipal) {
            return;
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            requireDriverProfileOwnerOrAdmin(user, driverId);
            return;
        }
        deny();
    }

    public void requireVehicleOwnerOrAdmin(
            AuthenticatedUser user,
            String vehicleId) {
        String driverId = vehicleService.getById(vehicleId).driverId();
        requireDriverProfileOwnerOrAdmin(user, driverId);
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user.role() == AccountRole.ADMIN;
    }

    private void deny() {
        throw new AccessDeniedException("Access denied");
    }
}
