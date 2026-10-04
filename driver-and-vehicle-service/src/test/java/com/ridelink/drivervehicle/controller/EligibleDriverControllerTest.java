package com.ridelink.drivervehicle.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ridelink.drivervehicle.dto.EligibleDriverResponse;
import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;
import com.ridelink.drivervehicle.model.VehicleType;
import com.ridelink.drivervehicle.service.EligibleDriverService;

@WebMvcTest(EligibleDriverController.class)
@AutoConfigureMockMvc(addFilters = false)
class EligibleDriverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EligibleDriverService service;

    @Test
    void validRequestReturnsOk() throws Exception {
        when(service.findEligibleDrivers("Colombo")).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/drivers/eligible")
                        .param("serviceArea", "Colombo"))
                .andExpect(status().isOk());
    }

    @Test
    void validRequestReturnsJsonArray() throws Exception {
        when(service.findEligibleDrivers("Colombo")).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/drivers/eligible")
                        .param("serviceArea", "Colombo"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].driverId").value("driver-1"))
                .andExpect(jsonPath("$[0].vehicleId").value("vehicle-1"));
    }

    @Test
    void noEligibleDriversReturnsOkWithEmptyArray() throws Exception {
        when(service.findEligibleDrivers("Colombo")).thenReturn(List.of());

        mockMvc.perform(get("/api/drivers/eligible")
                        .param("serviceArea", "Colombo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void blankServiceAreaReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/drivers/eligible")
                        .param("serviceArea", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/drivers/eligible"));
    }

    @Test
    void missingServiceAreaReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/drivers/eligible"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/drivers/eligible"));
    }

    private EligibleDriverResponse response() {
        return new EligibleDriverResponse(
                "driver-1",
                "account-1",
                "Colombo",
                6.9271,
                79.8612,
                DriverAvailabilityStatus.AVAILABLE,
                "vehicle-1",
                "CAB-1234",
                VehicleType.CAR);
    }
}
