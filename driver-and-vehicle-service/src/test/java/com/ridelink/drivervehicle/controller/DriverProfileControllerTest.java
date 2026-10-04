package com.ridelink.drivervehicle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ridelink.drivervehicle.dto.CreateDriverProfileRequest;
import com.ridelink.drivervehicle.dto.DriverProfileResponse;
import com.ridelink.drivervehicle.dto.UpdateDriverAvailabilityRequest;
import com.ridelink.drivervehicle.dto.UpdateDriverLocationRequest;
import com.ridelink.drivervehicle.exception.DriverProfileNotFoundException;
import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;
import com.ridelink.drivervehicle.security.AuthorizationService;
import com.ridelink.drivervehicle.service.DriverProfileService;

@WebMvcTest(DriverProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class DriverProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DriverProfileService service;

    @MockitoBean
    private AuthorizationService authorizationService;

    @Test
    void createReturnsCreated() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        DriverProfileResponse response = new DriverProfileResponse(
                "driver-1", "account-1", "LIC-123", "Colombo",
                DriverAvailabilityStatus.UNAVAILABLE, null, null, null, now, now);
        when(service.create(any(CreateDriverProfileRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "account-1",
                                  "licenseNumber": "LIC-123",
                                  "serviceArea": "Colombo"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("driver-1"))
                .andExpect(jsonPath("$.accountId").value("account-1"));
    }

    @Test
    void createReturnsBadRequestWithConsistentErrorBodyForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": " ",
                                  "licenseNumber": "",
                                  "serviceArea": "Colombo"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/drivers"));
    }

    @Test
    void getByIdReturnsOk() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(service.getById("driver-1")).thenReturn(new DriverProfileResponse(
                "driver-1", "account-1", "LIC-123", "Colombo",
                DriverAvailabilityStatus.UNAVAILABLE, null, null, null, now, now));

        mockMvc.perform(get("/api/drivers/driver-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("driver-1"));
        verify(service).getById("driver-1");
    }

    @Test
    void getByIdReturnsNotFoundWithConsistentErrorBody() throws Exception {
        when(service.getById("missing"))
                .thenThrow(new DriverProfileNotFoundException("Driver profile not found with id: missing"));

        mockMvc.perform(get("/api/drivers/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/drivers/missing"));
    }

    @Test
    void updateAvailabilityReturnsOk() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(service.updateAvailability(
                "driver-1",
                new UpdateDriverAvailabilityRequest(DriverAvailabilityStatus.AVAILABLE)))
                .thenReturn(new DriverProfileResponse(
                        "driver-1",
                        "account-1",
                        "LIC-123",
                        "Colombo",
                        DriverAvailabilityStatus.AVAILABLE,
                        null,
                        null,
                        null,
                        now,
                        now));

        mockMvc.perform(patch("/api/drivers/driver-1/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "availabilityStatus": "AVAILABLE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("driver-1"))
                .andExpect(jsonPath("$.availabilityStatus").value("AVAILABLE"));
    }

    @Test
    void updateAvailabilityReturnsBadRequestWhenStatusIsMissing() throws Exception {
        mockMvc.perform(patch("/api/drivers/driver-1/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("availabilityStatus: availabilityStatus is required"))
                .andExpect(jsonPath("$.path").value("/api/drivers/driver-1/availability"));
    }

    @Test
    void updateAvailabilityReturnsBadRequestWhenStatusIsInvalid() throws Exception {
        mockMvc.perform(patch("/api/drivers/driver-1/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "availabilityStatus": "BUSY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/drivers/driver-1/availability"));
    }

    @Test
    void updateAvailabilityReturnsNotFoundWhenDriverDoesNotExist() throws Exception {
        when(service.updateAvailability(
                "missing",
                new UpdateDriverAvailabilityRequest(DriverAvailabilityStatus.AVAILABLE)))
                .thenThrow(new DriverProfileNotFoundException(
                        "Driver profile not found with id: missing"));

        mockMvc.perform(patch("/api/drivers/missing/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "availabilityStatus": "AVAILABLE"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/drivers/missing/availability"));
    }

    @Test
    void updateLocationReturnsOk() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(service.updateLocation(
                "driver-1",
                new UpdateDriverLocationRequest(6.9271, 79.8612)))
                .thenReturn(locationResponse(now));

        mockMvc.perform(patch("/api/drivers/driver-1/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLocationRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("driver-1"))
                .andExpect(jsonPath("$.latitude").value(6.9271))
                .andExpect(jsonPath("$.longitude").value(79.8612))
                .andExpect(jsonPath("$.locationUpdatedAt").exists());
    }

    @Test
    void updateLocationReturnsBadRequestWhenLatitudeIsBelowMinimum() throws Exception {
        assertInvalidLocation("""
                {
                  "latitude": -90.1,
                  "longitude": 79.8612
                }
                """);
    }

    @Test
    void updateLocationReturnsBadRequestWhenLatitudeIsAboveMaximum() throws Exception {
        assertInvalidLocation("""
                {
                  "latitude": 90.1,
                  "longitude": 79.8612
                }
                """);
    }

    @Test
    void updateLocationReturnsBadRequestWhenLongitudeIsBelowMinimum() throws Exception {
        assertInvalidLocation("""
                {
                  "latitude": 6.9271,
                  "longitude": -180.1
                }
                """);
    }

    @Test
    void updateLocationReturnsBadRequestWhenLongitudeIsAboveMaximum() throws Exception {
        assertInvalidLocation("""
                {
                  "latitude": 6.9271,
                  "longitude": 180.1
                }
                """);
    }

    @Test
    void updateLocationReturnsBadRequestWhenLatitudeIsMissing() throws Exception {
        assertInvalidLocation("""
                {
                  "longitude": 79.8612
                }
                """);
    }

    @Test
    void updateLocationReturnsBadRequestWhenLongitudeIsMissing() throws Exception {
        assertInvalidLocation("""
                {
                  "latitude": 6.9271
                }
                """);
    }

    @Test
    void updateLocationReturnsBadRequestForMalformedNumericJson() throws Exception {
        assertInvalidLocation("""
                {
                  "latitude": "north",
                  "longitude": 79.8612
                }
                """);
    }

    @Test
    void updateLocationReturnsNotFoundWhenDriverDoesNotExist() throws Exception {
        when(service.updateLocation(
                "missing",
                new UpdateDriverLocationRequest(6.9271, 79.8612)))
                .thenThrow(new DriverProfileNotFoundException(
                        "Driver profile not found with id: missing"));

        mockMvc.perform(patch("/api/drivers/missing/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLocationRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/drivers/missing/location"));
    }

    private void assertInvalidLocation(String requestBody) throws Exception {
        mockMvc.perform(patch("/api/drivers/driver-1/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/drivers/driver-1/location"));
    }

    private DriverProfileResponse locationResponse(LocalDateTime now) {
        return new DriverProfileResponse(
                "driver-1",
                "account-1",
                "LIC-123",
                "Colombo",
                DriverAvailabilityStatus.AVAILABLE,
                6.9271,
                79.8612,
                now,
                now.minusDays(1),
                now);
    }

    private String validLocationRequest() {
        return """
                {
                  "latitude": 6.9271,
                  "longitude": 79.8612
                }
                """;
    }
}
