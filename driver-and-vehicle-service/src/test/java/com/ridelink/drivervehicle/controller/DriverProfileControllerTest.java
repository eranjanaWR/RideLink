package com.ridelink.drivervehicle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ridelink.drivervehicle.dto.CreateDriverProfileRequest;
import com.ridelink.drivervehicle.dto.DriverProfileResponse;
import com.ridelink.drivervehicle.exception.DriverProfileNotFoundException;
import com.ridelink.drivervehicle.service.DriverProfileService;

@WebMvcTest(DriverProfileController.class)
class DriverProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DriverProfileService service;

    @Test
    void createReturnsCreated() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        DriverProfileResponse response = new DriverProfileResponse(
                "driver-1", "account-1", "LIC-123", "Colombo", now, now);
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
                "driver-1", "account-1", "LIC-123", "Colombo", now, now));

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
}
