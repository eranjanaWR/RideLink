package com.ridelink.farepayment.controller;

import java.net.URI;

import com.ridelink.farepayment.dto.CreatePaymentRequest;
import com.ridelink.farepayment.dto.PaymentResponse;
import com.ridelink.farepayment.exception.ApiError;
import com.ridelink.farepayment.security.FarePaymentAuthorizationService;
import com.ridelink.farepayment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Simulated CASH and CARD payment records. No external payment gateway is used.")
public class PaymentController {
    private final PaymentService paymentService;
    private final FarePaymentAuthorizationService authorizationService;

    public PaymentController(
            PaymentService paymentService,
            FarePaymentAuthorizationService authorizationService
    ) {
        this.paymentService = paymentService;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "Create a simulated payment",
            description = "ADMIN JWT or trusted Ride Service key required. Creates one PENDING payment record for "
                    + "a ride. CASH and CARD are logical methods only; no external payment gateway is contacted. "
                    + "Only one payment record is allowed per ride.",
            security = {
                    @SecurityRequirement(name = "bearerAuth"),
                    @SecurityRequirement(name = "internalServiceKey")
            })
    @ApiResponse(responseCode = "201", description = "Payment created",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid payment request",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "A payment already exists for the ride",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected failure",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            Authentication authentication,
            @Valid @RequestBody CreatePaymentRequest request) {
        authorizationService.requireAdminOrInternal(authentication);
        PaymentResponse response = paymentService.createPayment(request);
        return ResponseEntity.created(URI.create("/api/payments/" + response.id())).body(response);
    }

    @Operation(summary = "Get payment by ID",
            description = "Available to the owning PASSENGER, ADMIN, or trusted Ride Service.",
            security = {
                    @SecurityRequirement(name = "bearerAuth"),
                    @SecurityRequirement(name = "internalServiceKey")
            })
    @ApiResponse(responseCode = "200", description = "Payment retrieved",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "404", description = "Payment not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected failure",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(Authentication authentication, @PathVariable String paymentId) {
        authorizationService.requirePaymentAccessById(authentication, paymentId);
        return paymentService.getPayment(paymentId);
    }

    @Operation(summary = "Get payment by ride ID",
            description = "Available to the owning PASSENGER, ADMIN, or trusted Ride Service.",
            security = {
                    @SecurityRequirement(name = "bearerAuth"),
                    @SecurityRequirement(name = "internalServiceKey")
            })
    @ApiResponse(responseCode = "200", description = "Payment retrieved",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "404", description = "Payment not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected failure",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/ride/{rideId}")
    public PaymentResponse getPaymentByRide(Authentication authentication, @PathVariable String rideId) {
        authorizationService.requirePaymentAccessByRide(authentication, rideId);
        return paymentService.getPaymentByRide(rideId);
    }

    @Operation(summary = "Complete a simulated payment",
            description = "Owning PASSENGER, ADMIN, or trusted Ride Service may transition a PENDING payment to "
                    + "COMPLETED without contacting an external payment gateway.",
            security = {
                    @SecurityRequirement(name = "bearerAuth"),
                    @SecurityRequirement(name = "internalServiceKey")
            })
    @ApiResponse(responseCode = "200", description = "Payment completed",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "404", description = "Payment not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Payment is not PENDING",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected failure",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/{paymentId}/complete")
    public PaymentResponse completePayment(Authentication authentication, @PathVariable String paymentId) {
        authorizationService.requirePaymentAccessById(authentication, paymentId);
        return paymentService.completePayment(paymentId);
    }

    @Operation(summary = "Fail a simulated payment",
            description = "Owning PASSENGER, ADMIN, or trusted Ride Service may transition a PENDING payment to "
                    + "FAILED without contacting an external payment gateway.",
            security = {
                    @SecurityRequirement(name = "bearerAuth"),
                    @SecurityRequirement(name = "internalServiceKey")
            })
    @ApiResponse(responseCode = "200", description = "Payment failed",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "404", description = "Payment not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Payment is not PENDING",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected failure",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/{paymentId}/fail")
    public PaymentResponse failPayment(Authentication authentication, @PathVariable String paymentId) {
        authorizationService.requirePaymentAccessById(authentication, paymentId);
        return paymentService.failPayment(paymentId);
    }
}
