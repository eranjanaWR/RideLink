package com.ridelink.ride.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridelink.ride.exception.FarePaymentServiceUnavailableException;
import com.ridelink.ride.exception.GlobalExceptionHandler;
import com.ridelink.ride.exception.InvalidFarePaymentResponseException;
import com.ridelink.ride.exception.PaymentConflictException;
import com.ridelink.ride.integration.driver.DriverServiceClient;
import com.ridelink.ride.integration.farepayment.FarePaymentServiceClient;
import com.ridelink.ride.integration.farepayment.dto.FinalFareResponse;
import com.ridelink.ride.integration.farepayment.dto.PaymentMethod;
import com.ridelink.ride.integration.farepayment.dto.PaymentResponse;
import com.ridelink.ride.integration.farepayment.dto.PaymentStatus;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.repository.RideRepository;
import com.ridelink.ride.security.RideAuthorizationService;
import com.ridelink.ride.service.RideService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(RideController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RideService.class, GlobalExceptionHandler.class})
class RideCompletionWithPaymentControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean RideRepository rideRepository;
    @MockitoBean DriverServiceClient driverServiceClient;
    @MockitoBean FarePaymentServiceClient farePaymentServiceClient;
    @MockitoBean RideAuthorizationService authorizationService;

    @BeforeEach
    void saveReturnsRide() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void validRequestReturns200() throws Exception {
        stubSuccessfulCompletion();
        postCompletion(request()).andExpect(status().isOk());
    }

    @Test
    void responseStatusIsCompleted() throws Exception {
        stubSuccessfulCompletion();
        postCompletion(request()).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void responseContainsFinalFare() throws Exception {
        stubSuccessfulCompletion();
        postCompletion(request()).andExpect(jsonPath("$.finalFare").value(1150.0));
    }

    @Test
    void responseContainsPaymentIdAndCompletionTime() throws Exception {
        stubSuccessfulCompletion();
        postCompletion(request())
                .andExpect(jsonPath("$.paymentId").value("payment-1"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void zeroDistanceReturns400() throws Exception {
        postCompletion(request().put("distanceKm", 0)).andExpect(status().isBadRequest());
        verifyNoInteractions(rideRepository, farePaymentServiceClient, driverServiceClient);
    }

    @Test
    void negativeDistanceReturns400() throws Exception {
        postCompletion(request().put("distanceKm", -1)).andExpect(status().isBadRequest());
    }

    @Test
    void missingDistanceReturns400() throws Exception {
        ObjectNode body = request();
        body.remove("distanceKm");
        postCompletion(body).andExpect(status().isBadRequest());
    }

    @Test
    void excessiveDistanceReturns400() throws Exception {
        postCompletion(request().put("distanceKm", 1000.01)).andExpect(status().isBadRequest());
    }

    @Test
    void missingPaymentMethodReturns400() throws Exception {
        ObjectNode body = request();
        body.remove("paymentMethod");
        postCompletion(body).andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedPaymentMethodReturns400() throws Exception {
        postCompletion(request().put("paymentMethod", "BANK_TRANSFER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/rides/ride-1/complete-with-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRideStateReturns409() throws Exception {
        Ride ride = ride();
        ride.setStatus(RideStatus.ACCEPTED);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        postCompletion(request())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Ride must be IN_PROGRESS before completion with payment"));
    }

    @Test
    void missingRideReturns404() throws Exception {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());
        mvc.perform(post("/api/rides/missing/complete-with-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void farePaymentServiceUnavailableReturns503() throws Exception {
        Ride ride = ride();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(farePaymentServiceClient.obtainFinalFare("ride-1", money("12.50")))
                .thenThrow(new FarePaymentServiceUnavailableException());
        postCompletion(request())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("Fare & Payment Service is currently unavailable"));
    }

    @Test
    void malformedFarePaymentResponseReturns502() throws Exception {
        Ride ride = ride();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(farePaymentServiceClient.obtainFinalFare("ride-1", money("12.50")))
                .thenThrow(new InvalidFarePaymentResponseException());
        postCompletion(request()).andExpect(status().isBadGateway());
    }

    @Test
    void incompatibleExistingPaymentReturns409() throws Exception {
        Ride ride = ride();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(farePaymentServiceClient.obtainFinalFare("ride-1", money("12.50")))
                .thenReturn(finalFare());
        when(farePaymentServiceClient.obtainPendingPayment(
                "ride-1", "passenger-1", money("1150.00"), PaymentMethod.CARD))
                .thenThrow(new PaymentConflictException());
        postCompletion(request()).andExpect(status().isConflict());
    }

    @Test
    void existingCompleteEndpointStillWorksWithoutFarePaymentCall() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride()));
        mvc.perform(post("/api/rides/ride-1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        verifyNoInteractions(farePaymentServiceClient);
    }

    @Test
    void driverReleaseFailureUsesExisting503Mapping() throws Exception {
        stubSuccessfulCompletion();
        doThrow(new com.ridelink.ride.exception.DriverServiceUnavailableException())
                .when(driverServiceClient).markDriverAvailable("driver-1");
        postCompletion(request()).andExpect(status().isServiceUnavailable());
    }

    private void stubSuccessfulCompletion() {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride()));
        when(farePaymentServiceClient.obtainFinalFare("ride-1", money("12.50")))
                .thenReturn(finalFare());
        when(farePaymentServiceClient.obtainPendingPayment(
                "ride-1", "passenger-1", money("1150.00"), PaymentMethod.CARD))
                .thenReturn(payment());
    }

    private ResultActions postCompletion(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/rides/ride-1/complete-with-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private ObjectNode request() {
        return mapper.createObjectNode()
                .put("distanceKm", new BigDecimal("12.50"))
                .put("paymentMethod", "CARD");
    }

    private FinalFareResponse finalFare() {
        return new FinalFareResponse("fare-1", "ride-1", money("12.50"),
                money("1150.00"), "LKR");
    }

    private PaymentResponse payment() {
        return new PaymentResponse("payment-1", "ride-1", "passenger-1",
                money("1150.00"), "LKR", PaymentMethod.CARD, PaymentStatus.PENDING);
    }

    private Ride ride() {
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setPassengerId("passenger-1");
        ride.setPickupLocation("Colombo Fort");
        ride.setDestinationLocation("Bambalapitiya");
        ride.setServiceArea("Colombo");
        ride.setDriverId("driver-1");
        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setRequestedAt(base.minusMinutes(30));
        ride.setAssignedAt(base.minusMinutes(20));
        ride.setAcceptedAt(base.minusMinutes(10));
        ride.setStartedAt(base);
        ride.setUpdatedAt(base);
        return ride;
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
