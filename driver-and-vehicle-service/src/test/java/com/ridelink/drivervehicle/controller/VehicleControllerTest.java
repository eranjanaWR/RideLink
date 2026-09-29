package com.ridelink.drivervehicle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ridelink.drivervehicle.dto.CreateVehicleRequest;
import com.ridelink.drivervehicle.dto.VehicleResponse;
import com.ridelink.drivervehicle.exception.DuplicateVehicleRegistrationException;
import com.ridelink.drivervehicle.model.VehicleType;
import com.ridelink.drivervehicle.service.VehicleService;

@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleService service;

    @Test
    void createReturnsCreated() throws Exception {
        when(service.create(any(CreateVehicleRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("vehicle-1"))
                .andExpect(jsonPath("$.driverId").value("driver-1"))
                .andExpect(jsonPath("$.vehicleType").value("CAR"));
    }

    @Test
    void createReturnsBadRequestForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "",
                                  "registrationNumber": "",
                                  "make": "",
                                  "model": "",
                                  "color": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/vehicles"));
    }

    @Test
    void createReturnsConflictForDuplicateRegistrationNumber() throws Exception {
        when(service.create(any(CreateVehicleRequest.class)))
                .thenThrow(new DuplicateVehicleRegistrationException("ABC-1234"));

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.path").value("/api/vehicles"));
    }

    @Test
    void getByDriverIdReturnsOk() throws Exception {
        when(service.getByDriverId("driver-1")).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/vehicles/driver/driver-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("vehicle-1"))
                .andExpect(jsonPath("$[0].driverId").value("driver-1"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        doNothing().when(service).delete("vehicle-1");

        mockMvc.perform(delete("/api/vehicles/vehicle-1"))
                .andExpect(status().isNoContent());
        verify(service).delete("vehicle-1");
    }

    private VehicleResponse response() {
        LocalDateTime now = LocalDateTime.now();
        return new VehicleResponse(
                "vehicle-1",
                "driver-1",
                "ABC-1234",
                "Toyota",
                "Prius",
                "Blue",
                VehicleType.CAR,
                now,
                now);
    }

    private String validCreateRequest() {
        return """
                {
                  "driverId": "driver-1",
                  "registrationNumber": "ABC-1234",
                  "make": "Toyota",
                  "model": "Prius",
                  "color": "Blue",
                  "vehicleType": "CAR"
                }
                """;
    }
}
