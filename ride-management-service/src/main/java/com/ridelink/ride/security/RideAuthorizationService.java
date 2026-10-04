package com.ridelink.ride.security;

import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.integration.driver.DriverServiceClient;
import com.ridelink.ride.integration.driver.dto.DriverProfileOwnershipResponse;
import com.ridelink.ride.service.RideService;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class RideAuthorizationService {

    private final RideService rideService;
    private final DriverServiceClient driverServiceClient;

    public RideAuthorizationService(
            RideService rideService,
            DriverServiceClient driverServiceClient
    ) {
        this.rideService = rideService;
        this.driverServiceClient = driverServiceClient;
    }

    public void requirePassengerOrAdminForCreate(
            AuthenticatedUser user,
            String passengerId
    ) {
        if (isAdmin(user)
                || (user.role() == AccountRole.PASSENGER
                && user.accountId().equals(passengerId))) {
            return;
        }
        deny();
    }

    public void requirePassengerListAccess(
            AuthenticatedUser user,
            String passengerId
    ) {
        requirePassengerOrAdminForCreate(user, passengerId);
    }

    public void requireRideViewer(Authentication authentication, String rideId) {
        RideResponse ride = rideService.getRideById(rideId);
        AuthenticatedUser user = user(authentication);
        if (isAdmin(user)
                || (user.role() == AccountRole.PASSENGER
                && user.accountId().equals(ride.passengerId()))) {
            return;
        }
        if (user.role() == AccountRole.DRIVER) {
            requireAssignedDriver(authentication, user, ride);
            return;
        }
        deny();
    }

    public void requirePassengerOwnerOrAdmin(Authentication authentication, String rideId) {
        RideResponse ride = rideService.getRideById(rideId);
        AuthenticatedUser user = user(authentication);
        if (isAdmin(user)
                || (user.role() == AccountRole.PASSENGER
                && user.accountId().equals(ride.passengerId()))) {
            return;
        }
        deny();
    }

    public void requireAssignedDriverOrAdmin(Authentication authentication, String rideId) {
        RideResponse ride = rideService.getRideById(rideId);
        AuthenticatedUser user = user(authentication);
        if (isAdmin(user)) {
            return;
        }
        if (user.role() == AccountRole.DRIVER) {
            requireAssignedDriver(authentication, user, ride);
            return;
        }
        deny();
    }

    public void requireCancelAccess(Authentication authentication, String rideId) {
        RideResponse ride = rideService.getRideById(rideId);
        AuthenticatedUser user = user(authentication);
        if (isAdmin(user)
                || (user.role() == AccountRole.PASSENGER
                && user.accountId().equals(ride.passengerId()))) {
            return;
        }
        if (user.role() == AccountRole.DRIVER) {
            requireAssignedDriver(authentication, user, ride);
            return;
        }
        deny();
    }

    private void requireAssignedDriver(
            Authentication authentication,
            AuthenticatedUser user,
            RideResponse ride
    ) {
        if (ride.driverId() == null || ride.driverId().isBlank()) {
            deny();
        }

        Optional<DriverProfileOwnershipResponse> profile =
                driverServiceClient.getDriverByAccountIdForUser(
                        user.accountId(),
                        bearerAuthorization(authentication)
                );
        if (profile.isPresent() && ride.driverId().equals(profile.get().id())) {
            return;
        }
        deny();
    }

    private AuthenticatedUser user(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        deny();
        return null;
    }

    private String bearerAuthorization(Authentication authentication) {
        if (authentication.getCredentials() instanceof String bearerAuthorization
                && bearerAuthorization.startsWith("Bearer ")) {
            return bearerAuthorization;
        }
        deny();
        return null;
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user.role() == AccountRole.ADMIN;
    }

    private void deny() {
        throw new AccessDeniedException("Access denied");
    }
}
