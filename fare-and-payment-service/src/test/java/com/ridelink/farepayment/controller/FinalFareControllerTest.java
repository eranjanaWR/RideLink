package com.ridelink.farepayment.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridelink.farepayment.dto.FinalFareRequest;
import com.ridelink.farepayment.dto.FinalFareResponse;
import com.ridelink.farepayment.exception.DuplicateFinalFareException;
import com.ridelink.farepayment.exception.FinalFareNotFoundException;
import com.ridelink.farepayment.exception.InvalidFinalFareException;
import com.ridelink.farepayment.security.FarePaymentAuthorizationService;
import com.ridelink.farepayment.service.FinalFareService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(FinalFareController.class)
@AutoConfigureMockMvc(addFilters = false)
class FinalFareControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean FinalFareService service;
    @MockitoBean FarePaymentAuthorizationService authorizationService;

    private FinalFareResponse response() {
        return new FinalFareResponse("fare-123", "ride-123", new BigDecimal("12.50"),
                new BigDecimal("150.00"), new BigDecimal("80.00"),
                new BigDecimal("1000.00"), new BigDecimal("1150.00"), "LKR",
                LocalDateTime.of(2026, 10, 1, 12, 0));
    }

    private ObjectNode request() {
        return mapper.createObjectNode().put("rideId", "ride-123")
                .put("distanceKm", new BigDecimal("12.50"));
    }

    private ResultActions calculate(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/fares/final").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    @Test void validPostReturns201() throws Exception {
        when(service.calculate(any())).thenReturn(response());
        calculate(request()).andExpect(status().isCreated());
    }
    @Test void responseContainsRideId() throws Exception {
        when(service.calculate(any())).thenReturn(response());
        calculate(request()).andExpect(jsonPath("$.rideId").value("ride-123"));
    }
    @Test void responseContainsFinalFare() throws Exception {
        when(service.calculate(any())).thenReturn(response());
        calculate(request()).andExpect(jsonPath("$.finalFare").isNumber())
                .andExpect(content().string(containsString("\"finalFare\":1150.00")));
    }
    @Test void responseContainsCurrency() throws Exception {
        when(service.calculate(any())).thenReturn(response());
        calculate(request()).andExpect(jsonPath("$.currency").value("LKR"));
    }
    @Test void responseContainsIdAndPricingSnapshot() throws Exception {
        when(service.calculate(any())).thenReturn(response());
        calculate(request()).andExpect(jsonPath("$.id").value("fare-123"))
                .andExpect(jsonPath("$.distanceKm").isNumber())
                .andExpect(jsonPath("$.baseFare").isNumber())
                .andExpect(jsonPath("$.perKmRate").isNumber())
                .andExpect(jsonPath("$.distanceFare").isNumber())
                .andExpect(jsonPath("$.createdAt").exists());
    }
    @Test void rideIdIsTrimmedBeforeServiceCall() throws Exception {
        when(service.calculate(any())).thenReturn(response());
        calculate(request().put("rideId", "  ride-123  ")).andExpect(status().isCreated());
        ArgumentCaptor<FinalFareRequest> captor = ArgumentCaptor.forClass(FinalFareRequest.class);
        verify(service).calculate(captor.capture());
        assertEquals("ride-123", captor.getValue().rideId());
    }
    @Test void blankRideIdReturns400() throws Exception {
        calculate(request().put("rideId", "   ")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void missingRideIdReturns400() throws Exception {
        ObjectNode body = request(); body.remove("rideId");
        calculate(body).andExpect(status().isBadRequest());
    }
    @Test void longRideIdReturns400() throws Exception {
        calculate(request().put("rideId", "r".repeat(101)))
                .andExpect(status().isBadRequest());
    }
    @Test void missingDistanceReturns400() throws Exception {
        ObjectNode body = request(); body.remove("distanceKm");
        calculate(body).andExpect(status().isBadRequest());
    }
    @Test void zeroDistanceReturns400() throws Exception {
        calculate(request().put("distanceKm", BigDecimal.ZERO)).andExpect(status().isBadRequest());
    }
    @Test void negativeDistanceReturns400() throws Exception {
        calculate(request().put("distanceKm", new BigDecimal("-1.00")))
                .andExpect(status().isBadRequest());
    }
    @Test void excessiveDistanceReturns400() throws Exception {
        calculate(request().put("distanceKm", new BigDecimal("1000.01")))
                .andExpect(status().isBadRequest());
    }
    @Test void duplicateRideReturns409() throws Exception {
        when(service.calculate(any())).thenThrow(new DuplicateFinalFareException());
        calculate(request()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A final fare already exists for this ride"));
    }
    @Test void invalidServiceRequestReturns400() throws Exception {
        when(service.calculate(any())).thenThrow(new InvalidFinalFareException("distanceKm is invalid"));
        calculate(request()).andExpect(status().isBadRequest());
    }
    @Test void getByIdReturns200() throws Exception {
        when(service.getById("fare-123")).thenReturn(response());
        mvc.perform(get("/api/fares/final/fare-123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("fare-123"));
    }
    @Test void missingIdReturns404() throws Exception {
        when(service.getById("missing")).thenThrow(new FinalFareNotFoundException());
        mvc.perform(get("/api/fares/final/missing")).andExpect(status().isNotFound());
    }
    @Test void getByRideReturns200() throws Exception {
        when(service.getByRideId("ride-123")).thenReturn(response());
        mvc.perform(get("/api/fares/final/ride/ride-123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rideId").value("ride-123"));
    }
    @Test void missingRideReturns404() throws Exception {
        when(service.getByRideId("missing")).thenThrow(new FinalFareNotFoundException());
        mvc.perform(get("/api/fares/final/ride/missing"))
                .andExpect(status().isNotFound());
    }
    @Test void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/fares/final").contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json")).andExpect(status().isBadRequest());
    }
    @Test void clientCannotSupplyFareValues() throws Exception {
        calculate(request().put("finalFare", new BigDecimal("0.01")))
                .andExpect(status().isBadRequest());
    }
    @Test void apiErrorHasRequiredFields() throws Exception {
        calculate(request().put("rideId", "   "))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/fares/final"));
    }
    @Test void unexpectedFailureIsSanitized() throws Exception {
        when(service.calculate(any())).thenThrow(new IllegalStateException("MongoDB internal details"));
        calculate(request()).andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("MongoDB internal details"))))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
