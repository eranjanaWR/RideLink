package com.ridelink.ride.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.exception.GlobalExceptionHandler;
import com.ridelink.ride.exception.InvalidDriverServiceResponseException;
import com.ridelink.ride.integration.driver.DriverServiceClient;
import com.ridelink.ride.integration.driver.dto.EligibleDriverResponse;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.repository.RideRepository;
import com.ridelink.ride.service.RideService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RideController.class)
@Import({RideService.class, GlobalExceptionHandler.class})
class RideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RideRepository rideRepository;

    @MockitoBean
    private DriverServiceClient driverServiceClient;

    @BeforeEach
    void saveReturnsPersistedArgument() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createRideReturnsCreatedAndInitialState() throws Exception {
        mockMvc.perform(post("/api/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/rides/.+")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.passengerId").value("account-passenger-001"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.driverId").doesNotExist())
                .andExpect(jsonPath("$.estimatedFare").doesNotExist())
                .andExpect(jsonPath("$.finalFare").doesNotExist())
                .andExpect(jsonPath("$.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.assignedAt").doesNotExist())
                .andExpect(jsonPath("$.acceptedAt").doesNotExist())
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createRideTrimsValues() throws Exception {
        CreateRideRequest request = new CreateRideRequest(
                " account-1 ", " Colombo Fort ", " Bambalapitiya ", " Colombo "
        );

        mockMvc.perform(post("/api/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passengerId").value("account-1"))
                .andExpect(jsonPath("$.pickupLocation").value("Colombo Fort"))
                .andExpect(jsonPath("$.destinationLocation").value("Bambalapitiya"))
                .andExpect(jsonPath("$.serviceArea").value("Colombo"));
    }

    @Test
    void missingPassengerIdReturnsBadRequest() throws Exception {
        assertInvalidCreate("{\"pickupLocation\":\"A\",\"destinationLocation\":\"B\",\"serviceArea\":\"Colombo\"}");
    }

    @Test
    void blankPassengerIdReturnsBadRequest() throws Exception {
        assertInvalidCreate(requestJson(" ", "A", "B", "Colombo"));
    }

    @Test
    void passengerIdOverMaximumLengthReturnsBadRequest() throws Exception {
        assertInvalidCreate(requestJson("x".repeat(101), "A", "B", "Colombo"));
    }

    @Test
    void missingPickupLocationReturnsBadRequest() throws Exception {
        assertInvalidCreate("{\"passengerId\":\"account-1\",\"destinationLocation\":\"B\",\"serviceArea\":\"Colombo\"}");
    }

    @Test
    void blankPickupLocationReturnsBadRequest() throws Exception {
        assertInvalidCreate(requestJson("account-1", " ", "B", "Colombo"));
    }

    @Test
    void pickupLocationOverMaximumLengthReturnsBadRequest() throws Exception {
        assertInvalidCreate(requestJson("account-1", "x".repeat(256), "B", "Colombo"));
    }

    @Test
    void missingDestinationLocationReturnsBadRequest() throws Exception {
        assertInvalidCreate("{\"passengerId\":\"account-1\",\"pickupLocation\":\"A\",\"serviceArea\":\"Colombo\"}");
    }

    @Test
    void blankDestinationLocationReturnsBadRequest() throws Exception {
        assertInvalidCreate(requestJson("account-1", "A", " ", "Colombo"));
    }

    @Test
    void destinationLocationOverMaximumLengthReturnsBadRequest() throws Exception {
        assertInvalidCreate(requestJson("account-1", "A", "x".repeat(256), "Colombo"));
    }

    @Test
    void missingServiceAreaReturnsBadRequest() throws Exception {
        assertInvalidCreate("{\"passengerId\":\"account-1\",\"pickupLocation\":\"A\",\"destinationLocation\":\"B\"}");
    }

    @Test
    void blankServiceAreaReturnsBadRequest() throws Exception {
        assertInvalidCreate(requestJson("account-1", "A", "B", " "));
    }

    @Test
    void serviceAreaOverMaximumLengthReturnsBadRequest() throws Exception {
        assertInvalidCreate(requestJson("account-1", "A", "B", "x".repeat(101)));
    }

    @Test
    void identicalLocationsReturnBadRequest() throws Exception {
        assertInvalidCreate(requestJson("account-1", "Colombo Fort", "Colombo Fort", "Colombo"));
    }

    @Test
    void locationsEqualIgnoringCaseReturnBadRequest() throws Exception {
        assertInvalidCreate(requestJson("account-1", "Colombo Fort", "colombo fort", "Colombo"));
    }

    @Test
    void locationsEqualAfterTrimmingReturnBadRequest() throws Exception {
        assertInvalidCreate(requestJson("account-1", "  Colombo Fort ", "Colombo Fort  ", "Colombo"));
    }

    @Test
    void malformedJsonReturnsBadRequestWithConsistentError() throws Exception {
        mockMvc.perform(post("/api/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passengerId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/rides"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void getExistingRideReturnsOk() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(requestedRide("ride-1", "account-1")));

        mockMvc.perform(get("/api/rides/ride-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ride-1"))
                .andExpect(jsonPath("$.passengerId").value("account-1"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void getMissingRideReturnsNotFoundWithConsistentError() throws Exception {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/rides/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("missing")))
                .andExpect(jsonPath("$.path").value("/api/rides/missing"));
    }

    @Test
    void getRidesByPassengerReturnsOkArray() throws Exception {
        when(rideRepository.findAllByPassengerId("account-1"))
                .thenReturn(List.of(requestedRide("ride-1", "account-1"), requestedRide("ride-2", "account-1")));

        mockMvc.perform(get("/api/rides/passenger/account-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("ride-1"))
                .andExpect(jsonPath("$[1].id").value("ride-2"));
    }

    @Test
    void getRidesForPassengerWithNoRidesReturnsEmptyArray() throws Exception {
        when(rideRepository.findAllByPassengerId("account-none")).thenReturn(List.of());

        mockMvc.perform(get("/api/rides/passenger/account-none"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void blankPassengerPathIdentifierReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/rides/passenger/{passengerId}", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedRepositoryFailureReturnsInternalServerErrorWithoutDetails() throws Exception {
        when(rideRepository.save(any(Ride.class))).thenThrow(new IllegalStateException("database detail"));

        mockMvc.perform(post("/api/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database detail")
                )));
    }

    @Test
    void assignDriverReturnsOkWithAssignedRide() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(requestedRide("ride-1", "account-1")));
        when(driverServiceClient.getEligibleDrivers("Colombo")).thenReturn(List.of(eligibleDriver("driver-1")));

        mockMvc.perform(post("/api/rides/ride-1/assign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ride-1"))
                .andExpect(jsonPath("$.driverId").value("driver-1"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void assignDriverReturnsNotFoundForUnknownRide() throws Exception {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/rides/missing/assign"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/rides/missing/assign"));
    }

    @Test
    void assignDriverReturnsConflictWhenNoDriverIsEligible() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(requestedRide("ride-1", "account-1")));
        when(driverServiceClient.getEligibleDrivers("Colombo")).thenReturn(List.of());

        mockMvc.perform(post("/api/rides/ride-1/assign"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Colombo")));
    }

    @Test
    void assignDriverReturnsConflictForInvalidRideState() throws Exception {
        Ride ride = requestedRide("ride-1", "account-1");
        ride.setStatus(RideStatus.ASSIGNED);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        mockMvc.perform(post("/api/rides/ride-1/assign"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ASSIGNED")));
    }

    @Test
    void assignDriverReturnsServiceUnavailableWhenDriverServiceCannotBeReached() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(requestedRide("ride-1", "account-1")));
        when(driverServiceClient.getEligibleDrivers("Colombo"))
                .thenThrow(new DriverServiceUnavailableException());

        mockMvc.perform(post("/api/rides/ride-1/assign"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Driver & Vehicle Service is currently unavailable"));
    }

    @Test
    void assignDriverReturnsBadGatewayForUnusableDriverResponse() throws Exception {
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(requestedRide("ride-1", "account-1")));
        when(driverServiceClient.getEligibleDrivers("Colombo"))
                .thenThrow(new InvalidDriverServiceResponseException());

        mockMvc.perform(post("/api/rides/ride-1/assign"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Driver & Vehicle Service returned an unusable response"));
    }

    private void assertInvalidCreate(String json) throws Exception {
        mockMvc.perform(post("/api/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/rides"));
    }

    private String requestJson(String passengerId, String pickup, String destination, String serviceArea)
            throws Exception {
        return objectMapper.writeValueAsString(new CreateRideRequest(passengerId, pickup, destination, serviceArea));
    }

    private CreateRideRequest validRequest() {
        return new CreateRideRequest(
                "account-passenger-001",
                "Colombo Fort",
                "Bambalapitiya",
                "Colombo"
        );
    }

    private Ride requestedRide(String id, String passengerId) {
        LocalDateTime now = LocalDateTime.now();
        Ride ride = new Ride();
        ride.setId(id);
        ride.setPassengerId(passengerId);
        ride.setPickupLocation("Colombo Fort");
        ride.setDestinationLocation("Bambalapitiya");
        ride.setServiceArea("Colombo");
        ride.setStatus(RideStatus.REQUESTED);
        ride.setRequestedAt(now);
        ride.setUpdatedAt(now);
        return ride;
    }

    private EligibleDriverResponse eligibleDriver(String driverId) {
        return new EligibleDriverResponse(
                driverId,
                "driver-account",
                "Colombo",
                6.9271,
                79.8612,
                "AVAILABLE",
                "vehicle-1",
                "TEST-CAB-001",
                "CAR"
        );
    }
}
