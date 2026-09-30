package com.ridelink.drivervehicle.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ridelink.drivervehicle.dto.EligibleDriverResponse;
import com.ridelink.drivervehicle.exception.ApiErrorResponse;
import com.ridelink.drivervehicle.service.EligibleDriverService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/drivers")
@Tag(name = "Eligible Drivers", description = "Find drivers eligible for ride assignment")
public class EligibleDriverController {

    private final EligibleDriverService service;

    public EligibleDriverController(EligibleDriverService service) {
        this.service = service;
    }

    @GetMapping("/eligible")
    @Operation(
            summary = "Find eligible drivers",
            description = "Returns drivers eligible for ride assignment. Eligibility currently requires AVAILABLE status, a case-insensitive matching service area, a simulated location, and at least one registered vehicle. The first vehicle returned for each driver is used. Geographic distance ranking is intentionally not implemented in this version.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Eligible drivers returned; an empty array means no drivers currently qualify"),
            @ApiResponse(
                    responseCode = "400",
                    description = "serviceArea is missing, blank, or too long",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<EligibleDriverResponse>> findEligibleDrivers(
            @Parameter(
                    description = "Service area to match after trimming; matching is case-insensitive",
                    required = true,
                    example = "Colombo")
            @RequestParam
            @NotBlank(message = "serviceArea is required and must not be blank")
            @Size(max = 100, message = "serviceArea must not exceed 100 characters")
            String serviceArea) {
        return ResponseEntity.ok(service.findEligibleDrivers(serviceArea));
    }
}
