package com.ridelink.farepayment.controller;

import com.ridelink.farepayment.dto.FareEstimateRequest;
import com.ridelink.farepayment.dto.FareEstimateResponse;
import com.ridelink.farepayment.exception.ApiError;
import com.ridelink.farepayment.service.FareEstimationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fares")
@SecurityRequirement(name = "bearerAuth")
public class FareController {
    private final FareEstimationService fareEstimationService;

    public FareController(FareEstimationService fareEstimationService) {
        this.fareEstimationService = fareEstimationService;
    }

    @Operation(summary = "Create fare estimate", description = "PASSENGER, DRIVER, or ADMIN JWT required. "
            + "The caller supplies simulated distanceKm; no external map service is used. The deterministic fare "
            + "uses BigDecimal with configurable currency and rates.")
    @ApiResponse(responseCode = "201", description = "Fare estimate created",
            content = @Content(schema = @Schema(implementation = FareEstimateResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid fare request",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected failure",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/estimate")
    public ResponseEntity<FareEstimateResponse> estimate(@Valid @RequestBody FareEstimateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fareEstimationService.estimate(request));
    }

    @Operation(summary = "Get fare estimate", description = "PASSENGER, DRIVER, or ADMIN JWT required. "
            + "Retrieve a persisted fare estimate by its ID.")
    @ApiResponse(responseCode = "200", description = "Fare estimate retrieved",
            content = @Content(schema = @Schema(implementation = FareEstimateResponse.class)))
    @ApiResponse(responseCode = "404", description = "Fare estimate not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected failure",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/estimates/{estimateId}")
    public FareEstimateResponse getEstimate(@PathVariable String estimateId) {
        return fareEstimationService.getEstimate(estimateId);
    }
}
