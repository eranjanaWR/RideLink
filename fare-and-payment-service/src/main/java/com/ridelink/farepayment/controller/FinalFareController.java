package com.ridelink.farepayment.controller;

import com.ridelink.farepayment.dto.FinalFareRequest;
import com.ridelink.farepayment.dto.FinalFareResponse;
import com.ridelink.farepayment.exception.ApiError;
import com.ridelink.farepayment.security.FarePaymentAuthorizationService;
import com.ridelink.farepayment.service.FinalFareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fares/final")
@Tag(name = "Final fares", description = "Deterministic final fares use the same configurable BigDecimal pricing "
        + "formula as estimates. Distance is supplied by the caller; no external maps service is used. "
        + "Only one final fare is allowed per ride.")
public class FinalFareController {
    private final FinalFareService service;
    private final FarePaymentAuthorizationService authorizationService;

    public FinalFareController(
            FinalFareService service,
            FarePaymentAuthorizationService authorizationService
    ) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    @Operation(
            summary = "Calculate final fare",
            description = "ADMIN JWT or trusted Ride Service key required. Calculates and persists one final fare "
                    + "per ride using supplied distance and the configured base, per-km, and minimum fares.",
            security = {
                    @SecurityRequirement(name = "bearerAuth"),
                    @SecurityRequirement(name = "internalServiceKey")
            })
    @ApiResponse(responseCode = "201", description = "Final fare created",
            content = @Content(schema = @Schema(implementation = FinalFareResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid final fare request",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Final fare already exists for ride",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    public ResponseEntity<FinalFareResponse> calculate(
            Authentication authentication,
            @Valid @RequestBody FinalFareRequest request
    ) {
        authorizationService.requireAdminOrInternal(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.calculate(request));
    }

    @Operation(
            summary = "Get final fare by ID",
            description = "ADMIN JWT or trusted Ride Service key required.",
            security = {
                    @SecurityRequirement(name = "bearerAuth"),
                    @SecurityRequirement(name = "internalServiceKey")
            })
    @ApiResponse(responseCode = "200", description = "Final fare retrieved",
            content = @Content(schema = @Schema(implementation = FinalFareResponse.class)))
    @ApiResponse(responseCode = "404", description = "Final fare not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{finalFareId}")
    public FinalFareResponse getById(Authentication authentication, @PathVariable String finalFareId) {
        authorizationService.requireAdminOrInternal(authentication);
        return service.getById(finalFareId);
    }

    @Operation(
            summary = "Get final fare by ride ID",
            description = "ADMIN JWT or trusted Ride Service key required.",
            security = {
                    @SecurityRequirement(name = "bearerAuth"),
                    @SecurityRequirement(name = "internalServiceKey")
            })
    @ApiResponse(responseCode = "200", description = "Ride final fare retrieved",
            content = @Content(schema = @Schema(implementation = FinalFareResponse.class)))
    @ApiResponse(responseCode = "404", description = "Final fare not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/ride/{rideId}")
    public FinalFareResponse getByRideId(Authentication authentication, @PathVariable String rideId) {
        authorizationService.requireAdminOrInternal(authentication);
        return service.getByRideId(rideId);
    }
}
