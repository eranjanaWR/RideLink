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
import com.ridelink.farepayment.dto.FareEstimateRequest;
import com.ridelink.farepayment.dto.FareEstimateResponse;
import com.ridelink.farepayment.exception.FareEstimateNotFoundException;
import com.ridelink.farepayment.exception.InvalidFareEstimateException;
import com.ridelink.farepayment.service.FareEstimationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.mockito.ArgumentCaptor;

@WebMvcTest(FareController.class)
class FareControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean FareEstimationService service;

    private FareEstimateResponse response() {
        return new FareEstimateResponse("estimate-id", "Colombo Fort", "Bambalapitiya",
                new BigDecimal("7.50"), new BigDecimal("150.00"), new BigDecimal("80.00"),
                new BigDecimal("600.00"), new BigDecimal("750.00"), "LKR",
                LocalDateTime.of(2026, 10, 1, 12, 0));
    }

    private ObjectNode request() {
        return mapper.createObjectNode().put("pickupLocation", "Colombo Fort")
                .put("destinationLocation", "Bambalapitiya").put("distanceKm", 7.50);
    }

    private ResultActions postEstimate(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/fares/estimate").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    @Test void validPostReturns201WithoutAuthentication() throws Exception {
        when(service.estimate(any())).thenReturn(response());
        postEstimate(request()).andExpect(status().isCreated());
        verify(service).estimate(any(FareEstimateRequest.class));
    }

    @Test void postResponseContainsEstimateId() throws Exception {
        when(service.estimate(any())).thenReturn(response());
        postEstimate(request()).andExpect(jsonPath("$.id").value("estimate-id"));
    }

    @Test void postResponseContainsEstimatedFare() throws Exception {
        when(service.estimate(any())).thenReturn(response());
        postEstimate(request()).andExpect(jsonPath("$.estimatedFare").value(750.0));
    }

    @Test void postResponseContainsCurrency() throws Exception {
        when(service.estimate(any())).thenReturn(response());
        postEstimate(request()).andExpect(jsonPath("$.currency").value("LKR"));
    }

    @Test void responseContainsRateSnapshotAndCreatedAt() throws Exception {
        when(service.estimate(any())).thenReturn(response());
        postEstimate(request()).andExpect(jsonPath("$.baseFare").value(150.0))
                .andExpect(jsonPath("$.perKmRate").value(80.0))
                .andExpect(jsonPath("$.distanceFare").value(600.0))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test void pickupAndDestinationAreTrimmedBeforeServiceCall() throws Exception {
        when(service.estimate(any())).thenReturn(response());
        postEstimate(request().put("pickupLocation", "  Colombo Fort  ")
                .put("destinationLocation", "  Bambalapitiya  ")).andExpect(status().isCreated());
        ArgumentCaptor<FareEstimateRequest> captor = ArgumentCaptor.forClass(FareEstimateRequest.class);
        verify(service).estimate(captor.capture());
        assertEquals("Colombo Fort", captor.getValue().pickupLocation());
        assertEquals("Bambalapitiya", captor.getValue().destinationLocation());
    }

    @Test void blankPickupReturns400() throws Exception {
        postEstimate(request().put("pickupLocation", "   ")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void missingPickupReturns400() throws Exception {
        ObjectNode body = request(); body.remove("pickupLocation");
        postEstimate(body).andExpect(status().isBadRequest());
    }

    @Test void blankDestinationReturns400() throws Exception {
        postEstimate(request().put("destinationLocation", "   ")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void missingDestinationReturns400() throws Exception {
        ObjectNode body = request(); body.remove("destinationLocation");
        postEstimate(body).andExpect(status().isBadRequest());
    }

    @Test void missingDistanceReturns400() throws Exception {
        ObjectNode body = request(); body.remove("distanceKm");
        postEstimate(body).andExpect(status().isBadRequest());
    }

    @Test void zeroDistanceReturns400() throws Exception {
        postEstimate(request().put("distanceKm", 0)).andExpect(status().isBadRequest());
    }

    @Test void negativeDistanceReturns400() throws Exception {
        postEstimate(request().put("distanceKm", -1)).andExpect(status().isBadRequest());
    }

    @Test void excessiveDistanceReturns400() throws Exception {
        postEstimate(request().put("distanceKm", 1000.01)).andExpect(status().isBadRequest());
    }

    @Test void longPickupReturns400() throws Exception {
        postEstimate(request().put("pickupLocation", "A".repeat(151)))
                .andExpect(status().isBadRequest());
    }

    @Test void longDestinationReturns400() throws Exception {
        postEstimate(request().put("destinationLocation", "B".repeat(151)))
                .andExpect(status().isBadRequest());
    }

    @Test void samePickupAndDestinationReturns400() throws Exception {
        when(service.estimate(any())).thenThrow(new InvalidFareEstimateException(
                "Pickup and destination must be different"));
        postEstimate(request().put("destinationLocation", "COLOMBO FORT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pickup and destination must be different"));
    }

    @Test void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/fares/estimate").contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test void nonNumericDistanceReturns400() throws Exception {
        postEstimate(request().put("distanceKm", "many"))
                .andExpect(status().isBadRequest());
    }

    @Test void unknownInputFieldReturns400() throws Exception {
        postEstimate(request().put("paymentStatus", "PAID"))
                .andExpect(status().isBadRequest());
    }

    @Test void getExistingEstimateReturns200() throws Exception {
        when(service.getEstimate("estimate-id")).thenReturn(response());
        mvc.perform(get("/api/fares/estimates/{estimateId}", "estimate-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("estimate-id"))
                .andExpect(jsonPath("$.estimatedFare").value(750.0));
    }

    @Test void getMissingEstimateReturns404() throws Exception {
        when(service.getEstimate("missing")).thenThrow(new FareEstimateNotFoundException());
        mvc.perform(get("/api/fares/estimates/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Fare estimate not found"));
    }

    @Test void errorContainsRequiredFields() throws Exception {
        postEstimate(request().put("distanceKm", 0))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/fares/estimate"));
    }

    @Test void unexpectedFailureReturnsSanitized500() throws Exception {
        when(service.estimate(any())).thenThrow(new IllegalStateException("MongoDB internal details"));
        postEstimate(request()).andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("MongoDB internal details"))))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
