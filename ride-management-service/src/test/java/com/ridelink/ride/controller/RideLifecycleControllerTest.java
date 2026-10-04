package com.ridelink.ride.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ridelink.ride.exception.GlobalExceptionHandler;
import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.exception.InvalidDriverServiceResponseException;
import com.ridelink.ride.integration.driver.DriverServiceClient;
import com.ridelink.ride.integration.farepayment.FarePaymentServiceClient;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.repository.RideRepository;
import com.ridelink.ride.security.RideAuthorizationService;
import com.ridelink.ride.service.RideService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RideController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RideService.class, GlobalExceptionHandler.class})
class RideLifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RideRepository rideRepository;

    @MockitoBean
    private DriverServiceClient driverServiceClient;

    @MockitoBean
    private FarePaymentServiceClient farePaymentServiceClient;

    @MockitoBean
    private RideAuthorizationService authorizationService;

    @BeforeEach
    void saveReturnsPersistedRide() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void acceptEndpointReturnsOk() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.ASSIGNED)));

        mockMvc.perform(post("/api/rides/ride-1/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty());
    }

    @Test
    void acceptEndpointReturnsConflictForInvalidState() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.REQUESTED)));

        mockMvc.perform(post("/api/rides/ride-1/accept"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Ride must be ASSIGNED before it can be accepted"));
    }

    @Test
    void acceptEndpointReturnsNotFoundForUnknownRide() throws Exception {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/rides/missing/accept"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void startEndpointReturnsOk() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.ACCEPTED)));

        mockMvc.perform(post("/api/rides/ride-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());
    }

    @Test
    void startEndpointReturnsConflictForInvalidState() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.ASSIGNED)));

        mockMvc.perform(post("/api/rides/ride-1/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void completeEndpointReturnsOk() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.IN_PROGRESS)));

        mockMvc.perform(post("/api/rides/ride-1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    void completeEndpointReturnsConflictForInvalidState() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.ACCEPTED)));

        mockMvc.perform(post("/api/rides/ride-1/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void completeEndpointReturnsServiceUnavailableWhenAvailabilityUpdateFails() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.IN_PROGRESS)));
        doThrow(new DriverServiceUnavailableException())
                .when(driverServiceClient).markDriverAvailable("driver-1");

        mockMvc.perform(post("/api/rides/ride-1/complete"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Driver & Vehicle Service is currently unavailable"));
    }

    @Test
    void completeEndpointReturnsBadGatewayForUnusableAvailabilityResponse() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.IN_PROGRESS)));
        doThrow(new InvalidDriverServiceResponseException())
                .when(driverServiceClient).markDriverAvailable("driver-1");

        mockMvc.perform(post("/api/rides/ride-1/complete"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void cancelEndpointReturnsOk() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.ACCEPTED)));

        mockMvc.perform(post("/api/rides/ride-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.driverId").value("driver-1"));
    }

    @Test
    void cancelEndpointReturnsConflictForInvalidState() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.IN_PROGRESS)));

        mockMvc.perform(post("/api/rides/ride-1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Ride cannot be cancelled from status IN_PROGRESS"));
    }

    @Test
    void cancelEndpointReturnsServiceUnavailableWhenAvailabilityUpdateFails() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(rideWithStatus(RideStatus.ASSIGNED)));
        doThrow(new DriverServiceUnavailableException())
                .when(driverServiceClient).markDriverAvailable("driver-1");

        mockMvc.perform(post("/api/rides/ride-1/cancel"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    private Ride rideWithStatus(RideStatus status) {
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setPassengerId("account-1");
        ride.setPickupLocation("Colombo Fort");
        ride.setDestinationLocation("Bambalapitiya");
        ride.setServiceArea("Colombo");
        ride.setStatus(status);
        ride.setRequestedAt(base);
        ride.setUpdatedAt(base);

        if (status != RideStatus.REQUESTED) {
            ride.setDriverId("driver-1");
            ride.setAssignedAt(base.plusMinutes(10));
        }
        if (status == RideStatus.ACCEPTED || status == RideStatus.IN_PROGRESS) {
            ride.setAcceptedAt(base.plusMinutes(20));
        }
        if (status == RideStatus.IN_PROGRESS) {
            ride.setStartedAt(base.plusMinutes(30));
        }
        return ride;
    }
}
