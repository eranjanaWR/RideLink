package com.ridelink.drivervehicle.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ridelink.drivervehicle.dto.CreateVehicleRequest;
import com.ridelink.drivervehicle.dto.UpdateVehicleRequest;
import com.ridelink.drivervehicle.dto.VehicleResponse;
import com.ridelink.drivervehicle.exception.ApiErrorResponse;
import com.ridelink.drivervehicle.service.VehicleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicles", description = "Manage vehicles belonging to RideLink driver profiles")
public class VehicleController {

    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Create a vehicle",
            description = "Creates a vehicle for an existing driver profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vehicle created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Registration number already exists",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> create(
            @Valid @RequestBody CreateVehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{vehicleId}")
    @Operation(
            summary = "Get a vehicle by ID",
            description = "Returns a vehicle using its Driver & Vehicle Service ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle returned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> getById(@PathVariable String vehicleId) {
        return ResponseEntity.ok(service.getById(vehicleId));
    }

    @GetMapping("/driver/{driverId}")
    @Operation(
            summary = "Get a driver's vehicles",
            description = "Returns all vehicles belonging to an existing driver profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle list returned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<VehicleResponse>> getByDriverId(@PathVariable String driverId) {
        return ResponseEntity.ok(service.getByDriverId(driverId));
    }

    @PutMapping("/{vehicleId}")
    @Operation(
            summary = "Update a vehicle",
            description = "Updates editable vehicle details without changing vehicle or driver ownership IDs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Registration number already exists",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> update(
            @PathVariable String vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return ResponseEntity.ok(service.update(vehicleId, request));
    }

    @DeleteMapping("/{vehicleId}")
    @Operation(summary = "Delete a vehicle", description = "Deletes a vehicle by ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vehicle deleted"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable String vehicleId) {
        service.delete(vehicleId);
        return ResponseEntity.noContent().build();
    }
}
