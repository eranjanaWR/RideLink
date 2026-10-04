package com.ridelink.ride.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ridelink.ride.config.SecurityConfig;
import com.ridelink.ride.controller.RideController;
import com.ridelink.ride.dto.CompleteRideWithPaymentRequest;
import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.exception.GlobalExceptionHandler;
import com.ridelink.ride.exception.InvalidDriverServiceResponseException;
import com.ridelink.ride.integration.driver.DriverServiceClient;
import com.ridelink.ride.integration.driver.dto.DriverProfileOwnershipResponse;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.service.RideService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({RideController.class, SwaggerProbeController.class})
@Import({
        SecurityConfig.class,
        JwtTokenValidator.class,
        SecurityErrorHandler.class,
        RideAuthorizationService.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "security.jwt.secret=test-only-jwt-secret-that-is-at-least-32-bytes-long"
})
class SecurityIntegrationTest {

    private static final String JWT_SECRET =
            "test-only-jwt-secret-that-is-at-least-32-bytes-long";
    private static final String RIDE_ID = "ride-1";
    private static final String PASSENGER_OWNER = "passenger-owner";
    private static final String OTHER_PASSENGER = "passenger-other";
    private static final String DRIVER_ACCOUNT = "driver-owner-account";
    private static final String OTHER_DRIVER_ACCOUNT = "driver-other-account";
    private static final String DRIVER_PROFILE = "driver-profile-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RideService rideService;

    @MockitoBean
    private DriverServiceClient driverServiceClient;

    @BeforeEach
    void setUpResponses() {
        RideResponse ride = rideResponse(RideStatus.ASSIGNED);
        when(rideService.getRideById(RIDE_ID)).thenReturn(ride);
        when(rideService.createRide(any(CreateRideRequest.class))).thenReturn(ride);
        when(rideService.getRidesByPassengerId(anyString())).thenReturn(List.of(ride));
        when(rideService.assignDriver(RIDE_ID)).thenReturn(ride);
        when(rideService.acceptRide(RIDE_ID)).thenReturn(ride);
        when(rideService.startRide(RIDE_ID)).thenReturn(ride);
        when(rideService.completeRide(RIDE_ID)).thenReturn(ride);
        when(rideService.completeRideWithPayment(
                anyString(), any(CompleteRideWithPaymentRequest.class))).thenReturn(ride);
        when(rideService.cancelRide(RIDE_ID)).thenReturn(ride);

        when(driverServiceClient.getDriverByAccountIdForUser(
                org.mockito.ArgumentMatchers.eq(DRIVER_ACCOUNT), anyString()))
                .thenReturn(Optional.of(new DriverProfileOwnershipResponse(
                        DRIVER_PROFILE, DRIVER_ACCOUNT)));
        when(driverServiceClient.getDriverByAccountIdForUser(
                org.mockito.ArgumentMatchers.eq(OTHER_DRIVER_ACCOUNT), anyString()))
                .thenReturn(Optional.of(new DriverProfileOwnershipResponse(
                        "different-driver-profile", OTHER_DRIVER_ACCOUNT)));
    }

    @Test
    void validPassengerTokenIsAccepted() throws Exception {
        performPassengerList(PASSENGER_OWNER, passengerOwnerToken())
                .andExpect(status().isOk());
    }

    @Test
    void validDriverTokenIsAccepted() throws Exception {
        performGetRide(driverToken()).andExpect(status().isOk());
    }

    @Test
    void validAdminTokenIsAccepted() throws Exception {
        performGetRide(adminToken()).andExpect(status().isOk());
    }

    @Test
    void missingJwtReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(get("/api/rides/" + RIDE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {
        performGetRide("malformed").andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        String token = jwt(
                PASSENGER_OWNER,
                AccountRole.PASSENGER,
                AccountStatus.ACTIVE,
                Instant.now().plusSeconds(60),
                "different-test-secret-that-is-also-at-least-32-bytes");

        performGetRide(token).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        String token = jwt(
                PASSENGER_OWNER,
                AccountRole.PASSENGER,
                AccountStatus.ACTIVE,
                Instant.now().minusSeconds(60),
                JWT_SECRET);

        performGetRide(token).andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedJwtReturnsJsonForbidden() throws Exception {
        String token = jwt(
                PASSENGER_OWNER,
                AccountRole.PASSENGER,
                AccountStatus.SUSPENDED,
                Instant.now().plusSeconds(60),
                JWT_SECRET);

        performGetRide(token)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void disabledJwtReturnsForbidden() throws Exception {
        String token = jwt(
                PASSENGER_OWNER,
                AccountRole.PASSENGER,
                AccountStatus.DISABLED,
                Instant.now().plusSeconds(60),
                JWT_SECRET);

        performGetRide(token).andExpect(status().isForbidden());
    }

    @Test
    void passengerCreatesOwnRide() throws Exception {
        performCreate(PASSENGER_OWNER, passengerOwnerToken())
                .andExpect(status().isCreated());
    }

    @Test
    void passengerCannotCreateRideForAnotherAccount() throws Exception {
        performCreate(OTHER_PASSENGER, passengerOwnerToken())
                .andExpect(status().isForbidden());
    }

    @Test
    void driverCannotCreateRide() throws Exception {
        performCreate(PASSENGER_OWNER, driverToken())
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateRideForAnotherAccount() throws Exception {
        performCreate(OTHER_PASSENGER, adminToken())
                .andExpect(status().isCreated());
    }

    @Test
    void passengerOwnerCanGetRide() throws Exception {
        performGetRide(passengerOwnerToken()).andExpect(status().isOk());
    }

    @Test
    void differentPassengerCannotGetRide() throws Exception {
        performGetRide(otherPassengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void assignedDriverCanGetRide() throws Exception {
        performGetRide(driverToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedDriverCannotGetRide() throws Exception {
        performGetRide(otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanGetRideWithoutDriverOwnershipLookup() throws Exception {
        performGetRide(adminToken()).andExpect(status().isOk());

        verify(driverServiceClient, never())
                .getDriverByAccountIdForUser(anyString(), anyString());
    }

    @Test
    void passengerCanGetOwnRideList() throws Exception {
        performPassengerList(PASSENGER_OWNER, passengerOwnerToken())
                .andExpect(status().isOk());
    }

    @Test
    void passengerCannotGetAnotherPassengersRideList() throws Exception {
        performPassengerList(OTHER_PASSENGER, passengerOwnerToken())
                .andExpect(status().isForbidden());
    }

    @Test
    void driverCannotGetPassengerRideList() throws Exception {
        performPassengerList(PASSENGER_OWNER, driverToken())
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanGetPassengerRideList() throws Exception {
        performPassengerList(PASSENGER_OWNER, adminToken())
                .andExpect(status().isOk());
    }

    @Test
    void passengerOwnerCanAssign() throws Exception {
        performAction("assign", passengerOwnerToken()).andExpect(status().isOk());
    }

    @Test
    void otherPassengerCannotAssign() throws Exception {
        performAction("assign", otherPassengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void driverCannotAssign() throws Exception {
        performAction("assign", driverToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanAssign() throws Exception {
        performAction("assign", adminToken()).andExpect(status().isOk());
    }

    @Test
    void assignedDriverCanAccept() throws Exception {
        performAction("accept", driverToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedDriverCannotAccept() throws Exception {
        performAction("accept", otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotAccept() throws Exception {
        performAction("accept", passengerOwnerToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccept() throws Exception {
        performAction("accept", adminToken()).andExpect(status().isOk());
    }

    @Test
    void assignedDriverCanStart() throws Exception {
        performAction("start", driverToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedDriverCannotStart() throws Exception {
        performAction("start", otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotStart() throws Exception {
        performAction("start", passengerOwnerToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanStart() throws Exception {
        performAction("start", adminToken()).andExpect(status().isOk());
    }

    @Test
    void assignedDriverCanComplete() throws Exception {
        performAction("complete", driverToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedDriverCannotComplete() throws Exception {
        performAction("complete", otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotComplete() throws Exception {
        performAction("complete", passengerOwnerToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanComplete() throws Exception {
        performAction("complete", adminToken()).andExpect(status().isOk());
    }

    @Test
    void assignedDriverCanCompleteWithPayment() throws Exception {
        performCompleteWithPayment(driverToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedDriverCannotCompleteWithPayment() throws Exception {
        performCompleteWithPayment(otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotCompleteWithPayment() throws Exception {
        performCompleteWithPayment(passengerOwnerToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanCompleteWithPayment() throws Exception {
        performCompleteWithPayment(adminToken()).andExpect(status().isOk());
    }

    @Test
    void owningPassengerCanCancelRequestedRide() throws Exception {
        when(rideService.getRideById(RIDE_ID)).thenReturn(rideResponse(RideStatus.REQUESTED));

        performAction("cancel", passengerOwnerToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedPassengerCannotCancel() throws Exception {
        performAction("cancel", otherPassengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void assignedDriverCanCancelAssignedRide() throws Exception {
        performAction("cancel", driverToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedDriverCannotCancel() throws Exception {
        performAction("cancel", otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanCancel() throws Exception {
        performAction("cancel", adminToken()).andExpect(status().isOk());
    }

    @Test
    void driverOwnershipLookupReceivesOriginalBearerHeader() throws Exception {
        String token = driverToken();

        performGetRide(token).andExpect(status().isOk());

        verify(driverServiceClient).getDriverByAccountIdForUser(
                DRIVER_ACCOUNT,
                "Bearer " + token);
    }

    @Test
    void matchingDriverProfileAllowsAccess() throws Exception {
        performGetRide(driverToken()).andExpect(status().isOk());
    }

    @Test
    void mismatchedDriverProfileDeniesAccess() throws Exception {
        performGetRide(otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void missingOperationalDriverProfileDeniesAccess() throws Exception {
        when(driverServiceClient.getDriverByAccountIdForUser(
                org.mockito.ArgumentMatchers.eq(DRIVER_ACCOUNT), anyString()))
                .thenReturn(Optional.empty());

        performGetRide(driverToken()).andExpect(status().isForbidden());
    }

    @Test
    void driverServiceUnavailableDuringOwnershipLookupReturnsServiceUnavailable() throws Exception {
        when(driverServiceClient.getDriverByAccountIdForUser(
                org.mockito.ArgumentMatchers.eq(DRIVER_ACCOUNT), anyString()))
                .thenThrow(new DriverServiceUnavailableException());

        performGetRide(driverToken())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void malformedDriverOwnershipResponseReturnsBadGateway() throws Exception {
        when(driverServiceClient.getDriverByAccountIdForUser(
                org.mockito.ArgumentMatchers.eq(DRIVER_ACCOUNT), anyString()))
                .thenThrow(new InvalidDriverServiceResponseException());

        performGetRide(driverToken())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void apiDocsRemainPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs/security-test"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUiRemainsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/security-test"))
                .andExpect(status().isOk());
    }

    @Test
    void missingAuthenticationDoesNotOfferHttpBasic() throws Exception {
        mockMvc.perform(get("/api/rides/" + RIDE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void missingAuthenticationDoesNotRedirectToFormLogin() throws Exception {
        mockMvc.perform(get("/api/rides/" + RIDE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
            String passengerId,
            String token
    ) throws Exception {
        return mockMvc.perform(post("/api/rides")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "passengerId": "%s",
                          "pickupLocation": "Colombo Fort",
                          "destinationLocation": "Bambalapitiya",
                          "serviceArea": "Colombo"
                        }
                        """.formatted(passengerId)));
    }

    private org.springframework.test.web.servlet.ResultActions performGetRide(String token)
            throws Exception {
        return mockMvc.perform(get("/api/rides/" + RIDE_ID)
                .header("Authorization", bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions performPassengerList(
            String passengerId,
            String token
    ) throws Exception {
        return mockMvc.perform(get("/api/rides/passenger/" + passengerId)
                .header("Authorization", bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions performAction(
            String action,
            String token
    ) throws Exception {
        return mockMvc.perform(post("/api/rides/" + RIDE_ID + "/" + action)
                .header("Authorization", bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions performCompleteWithPayment(
            String token
    ) throws Exception {
        return mockMvc.perform(post("/api/rides/" + RIDE_ID + "/complete-with-payment")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"distanceKm":12.5,"paymentMethod":"CARD"}
                        """));
    }

    private RideResponse rideResponse(RideStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new RideResponse(
                RIDE_ID,
                PASSENGER_OWNER,
                "Colombo Fort",
                "Bambalapitiya",
                "Colombo",
                DRIVER_PROFILE,
                status,
                null,
                null,
                null,
                now,
                now,
                null,
                null,
                null,
                null,
                now);
    }

    private String passengerOwnerToken() {
        return activeJwt(PASSENGER_OWNER, AccountRole.PASSENGER);
    }

    private String otherPassengerToken() {
        return activeJwt(OTHER_PASSENGER, AccountRole.PASSENGER);
    }

    private String driverToken() {
        return activeJwt(DRIVER_ACCOUNT, AccountRole.DRIVER);
    }

    private String otherDriverToken() {
        return activeJwt(OTHER_DRIVER_ACCOUNT, AccountRole.DRIVER);
    }

    private String adminToken() {
        return activeJwt("admin-account", AccountRole.ADMIN);
    }

    private String activeJwt(String accountId, AccountRole role) {
        return jwt(
                accountId,
                role,
                AccountStatus.ACTIVE,
                Instant.now().plusSeconds(300),
                JWT_SECRET);
    }

    private String jwt(
            String accountId,
            AccountRole role,
            AccountStatus status,
            Instant expiration,
            String secret
    ) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(accountId)
                .claim("email", accountId + "@example.com")
                .claim("role", role.name())
                .claim("status", status.name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
