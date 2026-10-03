package com.ridelink.farepayment.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridelink.farepayment.dto.CreatePaymentRequest;
import com.ridelink.farepayment.dto.PaymentResponse;
import com.ridelink.farepayment.exception.DuplicatePaymentException;
import com.ridelink.farepayment.exception.InvalidPaymentStateException;
import com.ridelink.farepayment.exception.PaymentNotFoundException;
import com.ridelink.farepayment.model.PaymentMethod;
import com.ridelink.farepayment.model.PaymentStatus;
import com.ridelink.farepayment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean PaymentService service;

    @Test void validCreateReturns201() throws Exception {
        when(service.createPayment(any())).thenReturn(pendingResponse());
        postPayment(request()).andExpect(status().isCreated());
    }

    @Test void createSetsPaymentLocationHeader() throws Exception {
        when(service.createPayment(any())).thenReturn(pendingResponse());
        postPayment(request()).andExpect(header().string("Location", "/api/payments/payment-1"));
    }

    @Test void createResponseHasPendingStatus() throws Exception {
        when(service.createPayment(any())).thenReturn(pendingResponse());
        postPayment(request()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test void createResponseHasConfiguredCurrency() throws Exception {
        when(service.createPayment(any())).thenReturn(pendingResponse());
        postPayment(request()).andExpect(jsonPath("$.currency").value("LKR"));
    }

    @Test void createResponseContainsPublicPaymentFields() throws Exception {
        when(service.createPayment(any())).thenReturn(pendingResponse());
        postPayment(request())
                .andExpect(jsonPath("$.id").value("payment-1"))
                .andExpect(jsonPath("$.rideId").value("ride-1"))
                .andExpect(jsonPath("$.passengerId").value("passenger-1"))
                .andExpect(jsonPath("$.amount").value(950.0))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test void identifiersAreTrimmedBeforeServiceCall() throws Exception {
        when(service.createPayment(any())).thenReturn(pendingResponse());
        postPayment(request().put("rideId", "  ride-1  ")
                .put("passengerId", "  passenger-1  ")).andExpect(status().isCreated());
        ArgumentCaptor<CreatePaymentRequest> captor = ArgumentCaptor.forClass(CreatePaymentRequest.class);
        verify(service).createPayment(captor.capture());
        assertEquals("ride-1", captor.getValue().rideId());
        assertEquals("passenger-1", captor.getValue().passengerId());
    }

    @Test void blankRideIdReturns400() throws Exception {
        postPayment(request().put("rideId", "   ")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void missingRideIdReturns400() throws Exception {
        ObjectNode body = request();
        body.remove("rideId");
        postPayment(body).andExpect(status().isBadRequest());
    }

    @Test void blankPassengerIdReturns400() throws Exception {
        postPayment(request().put("passengerId", "   ")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void missingPassengerIdReturns400() throws Exception {
        ObjectNode body = request();
        body.remove("passengerId");
        postPayment(body).andExpect(status().isBadRequest());
    }

    @Test void zeroAmountReturns400() throws Exception {
        postPayment(request().put("amount", BigDecimal.ZERO)).andExpect(status().isBadRequest());
    }

    @Test void negativeAmountReturns400() throws Exception {
        postPayment(request().put("amount", new BigDecimal("-0.01")))
                .andExpect(status().isBadRequest());
    }

    @Test void excessiveAmountReturns400() throws Exception {
        postPayment(request().put("amount", new BigDecimal("1000000.01")))
                .andExpect(status().isBadRequest());
    }

    @Test void missingAmountReturns400() throws Exception {
        ObjectNode body = request();
        body.remove("amount");
        postPayment(body).andExpect(status().isBadRequest());
    }

    @Test void missingMethodReturns400() throws Exception {
        ObjectNode body = request();
        body.remove("method");
        postPayment(body).andExpect(status().isBadRequest());
    }

    @Test void unsupportedMethodReturns400() throws Exception {
        postPayment(request().put("method", "BANK_TRANSFER"))
                .andExpect(status().isBadRequest());
    }

    @Test void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test void clientSuppliedStatusReturns400() throws Exception {
        postPayment(request().put("status", "COMPLETED"))
                .andExpect(status().isBadRequest());
    }

    @Test void duplicateRideReturns409() throws Exception {
        when(service.createPayment(any())).thenThrow(new DuplicatePaymentException("ride-1"));
        postPayment(request()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("ride-1")));
    }

    @Test void getByIdReturns200() throws Exception {
        when(service.getPayment("payment-1")).thenReturn(pendingResponse());
        mvc.perform(get("/api/payments/payment-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("payment-1"));
    }

    @Test void getMissingPaymentReturns404() throws Exception {
        when(service.getPayment("missing")).thenThrow(new PaymentNotFoundException());
        mvc.perform(get("/api/payments/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Payment not found"));
    }

    @Test void getByRideReturns200() throws Exception {
        when(service.getPaymentByRide("ride-1")).thenReturn(pendingResponse());
        mvc.perform(get("/api/payments/ride/ride-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId").value("ride-1"));
    }

    @Test void getMissingRidePaymentReturns404() throws Exception {
        when(service.getPaymentByRide("missing")).thenThrow(new PaymentNotFoundException());
        mvc.perform(get("/api/payments/ride/missing"))
                .andExpect(status().isNotFound());
    }

    @Test void completePendingPaymentReturns200() throws Exception {
        when(service.completePayment("payment-1")).thenReturn(completedResponse());
        mvc.perform(post("/api/payments/payment-1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test void completeInvalidStateReturns409() throws Exception {
        when(service.completePayment("payment-1"))
                .thenThrow(new InvalidPaymentStateException(PaymentStatus.COMPLETED, "completed"));
        mvc.perform(post("/api/payments/payment-1/complete"))
                .andExpect(status().isConflict());
    }

    @Test void completeMissingPaymentReturns404() throws Exception {
        when(service.completePayment("missing")).thenThrow(new PaymentNotFoundException());
        mvc.perform(post("/api/payments/missing/complete"))
                .andExpect(status().isNotFound());
    }

    @Test void failPendingPaymentReturns200() throws Exception {
        when(service.failPayment("payment-1")).thenReturn(failedResponse());
        mvc.perform(post("/api/payments/payment-1/fail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test void failInvalidStateReturns409() throws Exception {
        when(service.failPayment("payment-1"))
                .thenThrow(new InvalidPaymentStateException(PaymentStatus.FAILED, "failed"));
        mvc.perform(post("/api/payments/payment-1/fail"))
                .andExpect(status().isConflict());
    }

    @Test void failMissingPaymentReturns404() throws Exception {
        when(service.failPayment("missing")).thenThrow(new PaymentNotFoundException());
        mvc.perform(post("/api/payments/missing/fail"))
                .andExpect(status().isNotFound());
    }

    @Test void paymentErrorUsesExistingApiErrorShape() throws Exception {
        postPayment(request().put("amount", BigDecimal.ZERO))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/payments"));
    }

    @Test void unexpectedFailureReturnsSanitized500() throws Exception {
        when(service.createPayment(any()))
                .thenThrow(new IllegalStateException("MongoDB internal details"));
        postPayment(request()).andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("MongoDB internal details"))))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    private ResultActions postPayment(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private ObjectNode request() {
        return mapper.createObjectNode().put("rideId", "ride-1")
                .put("passengerId", "passenger-1")
                .put("amount", new BigDecimal("950.00"))
                .put("method", "CARD");
    }

    private PaymentResponse pendingResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 12, 0);
        return response(PaymentStatus.PENDING, now, null);
    }

    private PaymentResponse completedResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 12, 30);
        return response(PaymentStatus.COMPLETED, now, now);
    }

    private PaymentResponse failedResponse() {
        return response(PaymentStatus.FAILED, LocalDateTime.of(2026, 10, 1, 12, 30), null);
    }

    private PaymentResponse response(PaymentStatus status, LocalDateTime updatedAt,
                                     LocalDateTime completedAt) {
        return new PaymentResponse("payment-1", "ride-1", "passenger-1",
                new BigDecimal("950.00"), "LKR", PaymentMethod.CARD, status,
                LocalDateTime.of(2026, 10, 1, 12, 0), updatedAt, completedAt);
    }
}
