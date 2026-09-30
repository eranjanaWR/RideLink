package com.ridelink.ride.controller;

import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.ApiErrorResponse;
import com.ridelink.ride.service.RideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rides")
@Tag(name = "Rides", description = "Create and retrieve RideLink ride requests")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    @Operation(
            summary = "Create a ride request",
            description = "Creates a ride in REQUESTED state. passengerId is an external Account Service identifier; "
                    + "no Account Service database lookup occurs. Creation does not assign a driver or calculate a fare."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ride request created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid ride request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RideResponse> createRide(@Valid @RequestBody CreateRideRequest request) {
        RideResponse response = rideService.createRide(request);
        return ResponseEntity.created(URI.create("/api/rides/" + response.id())).body(response);
    }

    @PostMapping("/{rideId}/assign")
    @Operation(
            summary = "Assign an eligible driver",
            description = "Assigns a driver only when the ride is REQUESTED. The Ride Service queries Driver & Vehicle "
                    + "Service using the ride's stored serviceArea, sorts eligible drivers by driverId, and selects the "
                    + "first result. Distance ranking is not implemented. This operation does not change the selected "
                    + "driver's availability and does not provide a distributed transaction."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver assigned successfully"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ride state does not allow assignment or no eligible driver is available",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Driver Service returned an unusable response",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Driver Service is unavailable",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RideResponse> assignDriver(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.assignDriver(rideId));
    }

    @GetMapping("/{rideId}")
    @Operation(summary = "Get a ride by ID", description = "Returns one persisted ride request by its Ride Service ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride found"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RideResponse> getRideById(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.getRideById(rideId));
    }

    @GetMapping("/passenger/{passengerId}")
    @Operation(
            summary = "Get rides by passenger",
            description = "Returns rides for an external Account Service passenger identifier. No Account Service "
                    + "database lookup occurs."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride list returned"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Passenger identifier is blank",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<List<RideResponse>> getRidesByPassengerId(@PathVariable String passengerId) {
        return ResponseEntity.ok(rideService.getRidesByPassengerId(passengerId));
    }
}
