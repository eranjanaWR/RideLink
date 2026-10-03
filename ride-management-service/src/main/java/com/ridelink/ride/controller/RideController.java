package com.ridelink.ride.controller;

import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.CompleteRideWithPaymentRequest;
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
@Tag(name = "Rides", description = "Create, retrieve, assign, and manage the lifecycle of RideLink rides")
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
                    + "first result. Distance ranking is not implemented. The selected driver is marked UNAVAILABLE "
                    + "before the ASSIGNED ride is saved. If saving fails, restoring availability is best-effort; this "
                    + "operation does not provide a distributed transaction."
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

    @PostMapping("/{rideId}/accept")
    @Operation(
            summary = "Accept an assigned ride",
            description = "Transitions a ride from ASSIGNED to ACCEPTED. The driver remains UNAVAILABLE; no additional "
                    + "Driver Service availability request is made."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride accepted"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ride is not ASSIGNED or has no assigned driver",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RideResponse> acceptRide(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.acceptRide(rideId));
    }

    @PostMapping("/{rideId}/start")
    @Operation(
            summary = "Start an accepted ride",
            description = "Transitions a ride from ACCEPTED to IN_PROGRESS. The driver remains UNAVAILABLE; no "
                    + "additional Driver Service availability request is made."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride started"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ride is not ACCEPTED",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RideResponse> startRide(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.startRide(rideId));
    }

    @PostMapping("/{rideId}/complete")
    @Operation(
            summary = "Complete an in-progress ride",
            description = "Marks the assigned driver AVAILABLE, then transitions the ride from IN_PROGRESS to "
                    + "COMPLETED. Completion does not calculate a final fare or process payment."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride completed"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ride is not IN_PROGRESS or has no valid assigned driver",
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
    public ResponseEntity<RideResponse> completeRide(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.completeRide(rideId));
    }

    @PostMapping("/{rideId}/complete-with-payment")
    @Operation(
            summary = "Complete a ride with a simulated payment",
            description = "For an IN_PROGRESS ride, obtains or reuses a final fare from Fare & Payment Service, "
                    + "creates or reuses a compatible PENDING simulated payment whose amount comes from that final "
                    + "fare, releases the driver, and completes the ride. Safe retries may reuse an existing final-fare "
                    + "or payment record; no distributed transaction is provided."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride completed with final fare and payment reference"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid distance or payment method",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ride state is invalid or an existing payment is incompatible",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "A downstream service returned an unusable response",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Fare & Payment Service or Driver Service is unavailable",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RideResponse> completeRideWithPayment(
            @PathVariable String rideId,
            @Valid @RequestBody CompleteRideWithPaymentRequest request
    ) {
        return ResponseEntity.ok(rideService.completeRideWithPayment(rideId, request));
    }

    @PostMapping("/{rideId}/cancel")
    @Operation(
            summary = "Cancel a ride",
            description = "Transitions REQUESTED, ASSIGNED, or ACCEPTED rides to CANCELLED. Cancellation preserves "
                    + "the assigned driver and earlier lifecycle timestamps. Cancelling an ASSIGNED or ACCEPTED ride "
                    + "first marks its driver AVAILABLE; cancelling a REQUESTED ride makes no Driver Service call."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ride cancelled"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ride cannot be cancelled from its current state or has no valid assigned driver",
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
    public ResponseEntity<RideResponse> cancelRide(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.cancelRide(rideId));
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
