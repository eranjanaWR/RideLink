package com.ridelink.drivervehicle.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

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

import com.ridelink.drivervehicle.config.SecurityConfig;
import com.ridelink.drivervehicle.controller.DriverProfileController;
import com.ridelink.drivervehicle.controller.EligibleDriverController;
import com.ridelink.drivervehicle.controller.VehicleController;
import com.ridelink.drivervehicle.dto.CreateDriverProfileRequest;
import com.ridelink.drivervehicle.dto.CreateVehicleRequest;
import com.ridelink.drivervehicle.dto.DriverProfileResponse;
import com.ridelink.drivervehicle.dto.EligibleDriverResponse;
import com.ridelink.drivervehicle.dto.UpdateDriverAvailabilityRequest;
import com.ridelink.drivervehicle.dto.UpdateDriverLocationRequest;
import com.ridelink.drivervehicle.dto.UpdateDriverProfileRequest;
import com.ridelink.drivervehicle.dto.UpdateVehicleRequest;
import com.ridelink.drivervehicle.dto.VehicleResponse;
import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;
import com.ridelink.drivervehicle.model.VehicleType;
import com.ridelink.drivervehicle.service.DriverProfileService;
import com.ridelink.drivervehicle.service.EligibleDriverService;
import com.ridelink.drivervehicle.service.VehicleService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest({
        DriverProfileController.class,
        EligibleDriverController.class,
        VehicleController.class,
        SwaggerProbeController.class
})
@Import({
        SecurityConfig.class,
        JwtTokenValidator.class,
        InternalServiceAuthenticator.class,
        SecurityErrorHandler.class,
        AuthorizationService.class
})
@TestPropertySource(properties = {
        "security.jwt.secret=test-only-jwt-secret-that-is-at-least-32-bytes-long",
        "security.internal.service-key=test-only-internal-service-key"
})
class SecurityIntegrationTest {

    private static final String JWT_SECRET =
            "test-only-jwt-secret-that-is-at-least-32-bytes-long";
    private static final String INTERNAL_KEY = "test-only-internal-service-key";
    private static final String DRIVER_ID = "driver-1";
    private static final String VEHICLE_ID = "vehicle-1";
    private static final String OWNER_ACCOUNT_ID = "account-owner";
    private static final String OTHER_ACCOUNT_ID = "account-other";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DriverProfileService driverProfileService;

    @MockitoBean
    private VehicleService vehicleService;

    @MockitoBean
    private EligibleDriverService eligibleDriverService;

    @BeforeEach
    void setUpResponses() {
        when(driverProfileService.getById(DRIVER_ID)).thenReturn(driverResponse());
        when(driverProfileService.getByAccountId(OWNER_ACCOUNT_ID)).thenReturn(driverResponse());
        when(driverProfileService.create(any(CreateDriverProfileRequest.class))).thenReturn(driverResponse());
        when(driverProfileService.update(any(String.class), any(UpdateDriverProfileRequest.class)))
                .thenReturn(driverResponse());
        when(driverProfileService.updateLocation(any(String.class), any(UpdateDriverLocationRequest.class)))
                .thenReturn(driverResponse());
        when(driverProfileService.updateAvailability(
                any(String.class), any(UpdateDriverAvailabilityRequest.class)))
                .thenReturn(driverResponse());

        when(vehicleService.getById(VEHICLE_ID)).thenReturn(vehicleResponse());
        when(vehicleService.getByDriverId(DRIVER_ID)).thenReturn(List.of(vehicleResponse()));
        when(vehicleService.create(any(CreateVehicleRequest.class))).thenReturn(vehicleResponse());
        when(vehicleService.update(any(String.class), any(UpdateVehicleRequest.class)))
                .thenReturn(vehicleResponse());
        when(eligibleDriverService.findEligibleDrivers("Colombo"))
                .thenReturn(List.of(eligibleResponse()));
    }

    @Test
    void validDriverTokenIsAccepted() throws Exception {
        performEligible(jwt(OWNER_ACCOUNT_ID, AccountRole.DRIVER, AccountStatus.ACTIVE))
                .andExpect(status().isOk());
    }

    @Test
    void validAdminTokenIsAccepted() throws Exception {
        performEligible(jwt("admin-1", AccountRole.ADMIN, AccountStatus.ACTIVE))
                .andExpect(status().isOk());
    }

    @Test
    void validPassengerTokenIsAccepted() throws Exception {
        performEligible(jwt("passenger-1", AccountRole.PASSENGER, AccountStatus.ACTIVE))
                .andExpect(status().isOk());
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/drivers/" + DRIVER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/drivers/" + DRIVER_ID)
                        .header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        String token = jwt(
                OWNER_ACCOUNT_ID,
                AccountRole.DRIVER,
                AccountStatus.ACTIVE,
                "different-test-secret-that-is-also-at-least-32-bytes");
        mockMvc.perform(get("/api/drivers/" + DRIVER_ID).header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        String token = token(
                OWNER_ACCOUNT_ID,
                AccountRole.DRIVER.name(),
                AccountStatus.ACTIVE.name(),
                Instant.now().minusSeconds(60),
                JWT_SECRET);
        mockMvc.perform(get("/api/drivers/" + DRIVER_ID).header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedTokenReturnsForbidden() throws Exception {
        performEligible(jwt(OWNER_ACCOUNT_ID, AccountRole.DRIVER, AccountStatus.SUSPENDED))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledTokenReturnsForbidden() throws Exception {
        performEligible(jwt(OWNER_ACCOUNT_ID, AccountRole.DRIVER, AccountStatus.DISABLED))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingSubjectReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/drivers/eligible")
                        .param("serviceArea", "Colombo")
                        .header("Authorization", bearer(token(
                                null,
                                AccountRole.DRIVER.name(),
                                AccountStatus.ACTIVE.name(),
                                Instant.now().plusSeconds(60),
                                JWT_SECRET))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingRoleReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/drivers/eligible")
                        .param("serviceArea", "Colombo")
                        .header("Authorization", bearer(token(
                                OWNER_ACCOUNT_ID,
                                null,
                                AccountStatus.ACTIVE.name(),
                                Instant.now().plusSeconds(60),
                                JWT_SECRET))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void driverCreatesOwnProfile() throws Exception {
        mockMvc.perform(post("/api/drivers")
                        .header("Authorization", bearer(ownerDriverToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDriverJson(OWNER_ACCOUNT_ID)))
                .andExpect(status().isCreated());
    }

    @Test
    void driverCannotCreateAnotherAccountProfile() throws Exception {
        mockMvc.perform(post("/api/drivers")
                        .header("Authorization", bearer(ownerDriverToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDriverJson(OTHER_ACCOUNT_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotCreateDriverProfile() throws Exception {
        mockMvc.perform(post("/api/drivers")
                        .header("Authorization", bearer(passengerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDriverJson(OWNER_ACCOUNT_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateDriverProfile() throws Exception {
        mockMvc.perform(post("/api/drivers")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDriverJson(OTHER_ACCOUNT_ID)))
                .andExpect(status().isCreated());
    }

    @Test
    void ownerCanGetDriverProfile() throws Exception {
        performGetDriver(ownerDriverToken()).andExpect(status().isOk());
    }

    @Test
    void otherDriverCannotGetDriverProfile() throws Exception {
        performGetDriver(otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotGetDriverProfileById() throws Exception {
        performGetDriver(passengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanGetDriverProfile() throws Exception {
        performGetDriver(adminToken()).andExpect(status().isOk());
    }

    @Test
    void accountOwnerCanLookupOwnDriverProfile() throws Exception {
        mockMvc.perform(get("/api/drivers/account/" + OWNER_ACCOUNT_ID)
                        .header("Authorization", bearer(ownerDriverToken())))
                .andExpect(status().isOk());
    }

    @Test
    void otherAccountCannotLookupDriverProfile() throws Exception {
        mockMvc.perform(get("/api/drivers/account/" + OWNER_ACCOUNT_ID)
                        .header("Authorization", bearer(otherDriverToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanLookupDriverProfileByAccount() throws Exception {
        mockMvc.perform(get("/api/drivers/account/" + OWNER_ACCOUNT_ID)
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanUpdateDriverProfile() throws Exception {
        performUpdateDriver(ownerDriverToken()).andExpect(status().isOk());
    }

    @Test
    void otherDriverCannotUpdateDriverProfile() throws Exception {
        performUpdateDriver(otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateDriverProfile() throws Exception {
        performUpdateDriver(adminToken()).andExpect(status().isOk());
    }

    @Test
    void ownerCanUpdateLocation() throws Exception {
        performLocationUpdate(ownerDriverToken()).andExpect(status().isOk());
    }

    @Test
    void otherDriverCannotUpdateLocation() throws Exception {
        performLocationUpdate(otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotUpdateLocation() throws Exception {
        performLocationUpdate(passengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateLocation() throws Exception {
        performLocationUpdate(adminToken()).andExpect(status().isOk());
    }

    @Test
    void ownerCanUpdateAvailability() throws Exception {
        performAvailabilityWithJwt(ownerDriverToken()).andExpect(status().isOk());
    }

    @Test
    void otherDriverCannotUpdateAvailability() throws Exception {
        performAvailabilityWithJwt(otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateAvailability() throws Exception {
        performAvailabilityWithJwt(adminToken()).andExpect(status().isOk());
    }

    @Test
    void internalServiceCanUpdateAvailability() throws Exception {
        mockMvc.perform(patch("/api/drivers/" + DRIVER_ID + "/availability")
                        .header("X-Internal-Service-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(availabilityJson()))
                .andExpect(status().isOk());
    }

    @Test
    void invalidInternalKeyCannotUpdateAvailability() throws Exception {
        mockMvc.perform(patch("/api/drivers/" + DRIVER_ID + "/availability")
                        .header("X-Internal-Service-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(availabilityJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noAuthenticationCannotUpdateAvailability() throws Exception {
        mockMvc.perform(patch("/api/drivers/" + DRIVER_ID + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(availabilityJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalServiceCanSearchEligibleDrivers() throws Exception {
        mockMvc.perform(get("/api/drivers/eligible")
                        .param("serviceArea", "Colombo")
                        .header("X-Internal-Service-Key", INTERNAL_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void noAuthenticationCannotSearchEligibleDrivers() throws Exception {
        mockMvc.perform(get("/api/drivers/eligible").param("serviceArea", "Colombo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInternalKeyCannotSearchEligibleDrivers() throws Exception {
        mockMvc.perform(get("/api/drivers/eligible")
                        .param("serviceArea", "Colombo")
                        .header("X-Internal-Service-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanCreateVehicle() throws Exception {
        performCreateVehicle(ownerDriverToken()).andExpect(status().isCreated());
    }

    @Test
    void otherDriverCannotCreateVehicle() throws Exception {
        performCreateVehicle(otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotCreateVehicle() throws Exception {
        performCreateVehicle(passengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateVehicle() throws Exception {
        performCreateVehicle(adminToken()).andExpect(status().isCreated());
    }

    @Test
    void ownerCanGetVehicle() throws Exception {
        performGetVehicle(ownerDriverToken()).andExpect(status().isOk());
    }

    @Test
    void otherDriverCannotGetVehicle() throws Exception {
        performGetVehicle(otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void ownerCanListVehicles() throws Exception {
        mockMvc.perform(get("/api/vehicles/driver/" + DRIVER_ID)
                        .header("Authorization", bearer(ownerDriverToken())))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanUpdateVehicle() throws Exception {
        performUpdateVehicle(ownerDriverToken()).andExpect(status().isOk());
    }

    @Test
    void otherDriverCannotUpdateVehicle() throws Exception {
        performUpdateVehicle(otherDriverToken()).andExpect(status().isForbidden());
    }

    @Test
    void ownerCanDeleteVehicle() throws Exception {
        performDeleteVehicle(ownerDriverToken()).andExpect(status().isNoContent());
    }

    @Test
    void passengerCannotDeleteVehicle() throws Exception {
        performDeleteVehicle(passengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanGetVehicle() throws Exception {
        performGetVehicle(adminToken()).andExpect(status().isOk());
    }

    @Test
    void adminCanUpdateVehicle() throws Exception {
        performUpdateVehicle(adminToken()).andExpect(status().isOk());
    }

    @Test
    void adminCanDeleteVehicle() throws Exception {
        performDeleteVehicle(adminToken()).andExpect(status().isNoContent());
    }

    @Test
    void swaggerApiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs/security-test"))
                .andExpect(status().isOk());
    }

    @Test
    void missingAuthenticationDoesNotSendBasicChallenge() throws Exception {
        mockMvc.perform(get("/api/drivers/" + DRIVER_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void missingAuthenticationDoesNotRedirectToFormLogin() throws Exception {
        mockMvc.perform(get("/api/drivers/" + DRIVER_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void securityErrorsUseJsonApiErrorFormat() throws Exception {
        mockMvc.perform(get("/api/drivers/" + DRIVER_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/drivers/" + DRIVER_ID));
    }

    private org.springframework.test.web.servlet.ResultActions performEligible(String token)
            throws Exception {
        return mockMvc.perform(get("/api/drivers/eligible")
                .param("serviceArea", "Colombo")
                .header("Authorization", bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions performGetDriver(String token)
            throws Exception {
        return mockMvc.perform(get("/api/drivers/" + DRIVER_ID)
                .header("Authorization", bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions performUpdateDriver(String token)
            throws Exception {
        return mockMvc.perform(put("/api/drivers/" + DRIVER_ID)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"licenseNumber":"LIC-2","serviceArea":"Colombo"}
                        """));
    }

    private org.springframework.test.web.servlet.ResultActions performLocationUpdate(String token)
            throws Exception {
        return mockMvc.perform(patch("/api/drivers/" + DRIVER_ID + "/location")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"latitude":6.9271,"longitude":79.8612}
                        """));
    }

    private org.springframework.test.web.servlet.ResultActions performAvailabilityWithJwt(String token)
            throws Exception {
        return mockMvc.perform(patch("/api/drivers/" + DRIVER_ID + "/availability")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(availabilityJson()));
    }

    private org.springframework.test.web.servlet.ResultActions performCreateVehicle(String token)
            throws Exception {
        return mockMvc.perform(post("/api/vehicles")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "driverId":"driver-1",
                          "registrationNumber":"CAB-1",
                          "make":"Toyota",
                          "model":"Axio",
                          "color":"White",
                          "vehicleType":"CAR"
                        }
                        """));
    }

    private org.springframework.test.web.servlet.ResultActions performGetVehicle(String token)
            throws Exception {
        return mockMvc.perform(get("/api/vehicles/" + VEHICLE_ID)
                .header("Authorization", bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions performUpdateVehicle(String token)
            throws Exception {
        return mockMvc.perform(put("/api/vehicles/" + VEHICLE_ID)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "registrationNumber":"CAB-1",
                          "make":"Toyota",
                          "model":"Axio",
                          "color":"Blue",
                          "vehicleType":"CAR"
                        }
                        """));
    }

    private org.springframework.test.web.servlet.ResultActions performDeleteVehicle(String token)
            throws Exception {
        return mockMvc.perform(delete("/api/vehicles/" + VEHICLE_ID)
                .header("Authorization", bearer(token)));
    }

    private DriverProfileResponse driverResponse() {
        LocalDateTime now = LocalDateTime.now();
        return new DriverProfileResponse(
                DRIVER_ID,
                OWNER_ACCOUNT_ID,
                "LIC-1",
                "Colombo",
                DriverAvailabilityStatus.AVAILABLE,
                6.9271,
                79.8612,
                now,
                now,
                now);
    }

    private VehicleResponse vehicleResponse() {
        LocalDateTime now = LocalDateTime.now();
        return new VehicleResponse(
                VEHICLE_ID,
                DRIVER_ID,
                "CAB-1",
                "Toyota",
                "Axio",
                "White",
                VehicleType.CAR,
                now,
                now);
    }

    private EligibleDriverResponse eligibleResponse() {
        return new EligibleDriverResponse(
                DRIVER_ID,
                OWNER_ACCOUNT_ID,
                "Colombo",
                6.9271,
                79.8612,
                DriverAvailabilityStatus.AVAILABLE,
                VEHICLE_ID,
                "CAB-1",
                VehicleType.CAR);
    }

    private String createDriverJson(String accountId) {
        return """
                {
                  "accountId":"%s",
                  "licenseNumber":"LIC-1",
                  "serviceArea":"Colombo"
                }
                """.formatted(accountId);
    }

    private String availabilityJson() {
        return """
                {"availabilityStatus":"AVAILABLE"}
                """;
    }

    private String ownerDriverToken() {
        return jwt(OWNER_ACCOUNT_ID, AccountRole.DRIVER, AccountStatus.ACTIVE);
    }

    private String otherDriverToken() {
        return jwt(OTHER_ACCOUNT_ID, AccountRole.DRIVER, AccountStatus.ACTIVE);
    }

    private String passengerToken() {
        return jwt("passenger-1", AccountRole.PASSENGER, AccountStatus.ACTIVE);
    }

    private String adminToken() {
        return jwt("admin-1", AccountRole.ADMIN, AccountStatus.ACTIVE);
    }

    private String jwt(String accountId, AccountRole role, AccountStatus status) {
        return jwt(accountId, role, status, JWT_SECRET);
    }

    private String jwt(
            String accountId,
            AccountRole role,
            AccountStatus status,
            String secret) {
        return token(
                accountId,
                role.name(),
                status.name(),
                Instant.now().plusSeconds(300),
                secret);
    }

    private String token(
            String accountId,
            String role,
            String accountStatus,
            Instant expiration,
            String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder()
                .claim("email", "test@example.com")
                .issuedAt(Date.from(Instant.now().minusSeconds(1)))
                .expiration(Date.from(expiration));
        if (accountId != null) {
            builder.subject(accountId);
        }
        if (role != null) {
            builder.claim("role", role);
        }
        if (accountStatus != null) {
            builder.claim("status", accountStatus);
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
