package com.ridelink.drivervehicle.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridelink.drivervehicle.dto.CreateDriverProfileRequest;
import com.ridelink.drivervehicle.dto.DriverProfileResponse;
import com.ridelink.drivervehicle.dto.UpdateDriverAvailabilityRequest;
import com.ridelink.drivervehicle.dto.UpdateDriverLocationRequest;
import com.ridelink.drivervehicle.dto.UpdateDriverProfileRequest;
import com.ridelink.drivervehicle.exception.ApiErrorResponse;
import com.ridelink.drivervehicle.security.AuthenticatedUser;
import com.ridelink.drivervehicle.security.AuthorizationService;
import com.ridelink.drivervehicle.service.DriverProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/drivers")
@Tag(name = "Driver Profiles", description = "Create and manage operational driver profiles")
@SecurityRequirement(name = "bearerAuth")
public class DriverProfileController {

    private final DriverProfileService service;
    private final AuthorizationService authorizationService;

    public DriverProfileController(
            DriverProfileService service,
            AuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @Operation(
            summary = "Create a driver profile",
            description = "Creates one operational driver profile for an Account Service account.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Driver profile created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "The account already has a driver profile",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DriverProfileResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateDriverProfileRequest request) {
        authorizationService.requireDriverOrAdminForAccount(user, request.accountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{driverId}")
    @Operation(
            summary = "Get a driver profile by profile ID",
            description = "Returns the driver profile identified by its Driver & Vehicle Service ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver profile returned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DriverProfileResponse> getById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String driverId) {
        authorizationService.requireDriverProfileOwnerOrAdmin(user, driverId);
        return ResponseEntity.ok(service.getById(driverId));
    }

    @GetMapping("/account/{accountId}")
    @Operation(
            summary = "Get a driver profile by account ID",
            description = "Returns the driver profile associated with an Account Service account ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver profile returned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DriverProfileResponse> getByAccountId(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String accountId) {
        authorizationService.requireAccountOwnerOrAdmin(user, accountId);
        return ResponseEntity.ok(service.getByAccountId(accountId));
    }

    @PutMapping("/{driverId}")
    @Operation(
            summary = "Update a driver profile",
            description = "Updates only the driver's license number and service area.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver profile updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DriverProfileResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String driverId,
            @Valid @RequestBody UpdateDriverProfileRequest request) {
        authorizationService.requireDriverProfileOwnerOrAdmin(user, driverId);
        return ResponseEntity.ok(service.update(driverId, request));
    }

    @PatchMapping("/{driverId}/availability")
    @Operation(
            summary = "Update driver availability",
            description = "Sets an existing driver profile to AVAILABLE or UNAVAILABLE. Accepts the owner DRIVER or ADMIN JWT, or trusted Ride Service authentication using X-Internal-Service-Key.",
            security = {
                    @SecurityRequirement(name = "bearerAuth"),
                    @SecurityRequirement(name = "internalServiceKey")
            })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver availability updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Availability status is missing or invalid",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DriverProfileResponse> updateAvailability(
            Authentication authentication,
            @PathVariable String driverId,
            @Valid @RequestBody UpdateDriverAvailabilityRequest request) {
        authorizationService.requireDriverOwnerAdminOrInternal(
                authentication,
                driverId);
        return ResponseEntity.ok(service.updateAvailability(driverId, request));
    }

    @PatchMapping("/{driverId}/location")
    @Operation(
            summary = "Update simulated driver location",
            description = "Updates simulated latitude and longitude for assignment and demonstration purposes only; no live GPS or mapping service is used.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simulated driver location updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Coordinates are missing, malformed, or outside their valid ranges",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DriverProfileResponse> updateLocation(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String driverId,
            @Valid @RequestBody UpdateDriverLocationRequest request) {
        authorizationService.requireDriverProfileOwnerOrAdmin(user, driverId);
        return ResponseEntity.ok(service.updateLocation(driverId, request));
    }
}
