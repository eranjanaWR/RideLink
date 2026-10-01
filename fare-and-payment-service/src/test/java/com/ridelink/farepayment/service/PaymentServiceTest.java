package com.ridelink.farepayment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.ridelink.farepayment.config.FareProperties;
import com.ridelink.farepayment.dto.CreatePaymentRequest;
import com.ridelink.farepayment.dto.PaymentResponse;
import com.ridelink.farepayment.exception.DuplicatePaymentException;
import com.ridelink.farepayment.exception.InvalidPaymentRequestException;
import com.ridelink.farepayment.exception.InvalidPaymentStateException;
import com.ridelink.farepayment.exception.PaymentNotFoundException;
import com.ridelink.farepayment.model.Payment;
import com.ridelink.farepayment.model.PaymentMethod;
import com.ridelink.farepayment.model.PaymentStatus;
import com.ridelink.farepayment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock PaymentRepository repository;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(repository, new FareProperties("LKR", money("150.00"),
                money("80.00"), money("250.00")));
        lenient().when(repository.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test void createsCashPayment() {
        assertEquals(PaymentMethod.CASH, service.createPayment(request(PaymentMethod.CASH)).method());
    }

    @Test void createsCardPayment() {
        assertEquals(PaymentMethod.CARD, service.createPayment(request(PaymentMethod.CARD)).method());
    }

    @Test void generatedIdIsUuid() {
        String id = service.createPayment(request(PaymentMethod.CARD)).id();
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test void configuredCurrencyIsUsed() {
        assertEquals("LKR", service.createPayment(request(PaymentMethod.CARD)).currency());
    }

    @Test void initialStatusIsPending() {
        assertEquals(PaymentStatus.PENDING, service.createPayment(request(PaymentMethod.CARD)).status());
    }

    @Test void createdAtIsSet() {
        LocalDateTime before = LocalDateTime.now();
        PaymentResponse response = service.createPayment(request(PaymentMethod.CARD));
        assertFalse(response.createdAt().isBefore(before));
        assertFalse(response.createdAt().isAfter(LocalDateTime.now()));
    }

    @Test void updatedAtEqualsCreatedAtOnCreation() {
        PaymentResponse response = service.createPayment(request(PaymentMethod.CARD));
        assertEquals(response.createdAt(), response.updatedAt());
    }

    @Test void completedAtIsInitiallyNull() {
        assertNull(service.createPayment(request(PaymentMethod.CARD)).completedAt());
    }

    @Test void rideIdIsTrimmed() {
        PaymentResponse response = service.createPayment(new CreatePaymentRequest(
                "  ride-1  ", "passenger-1", money("950.00"), PaymentMethod.CARD));
        assertEquals("ride-1", response.rideId());
        verify(repository).findByRideId("ride-1");
    }

    @Test void passengerIdIsTrimmed() {
        PaymentResponse response = service.createPayment(new CreatePaymentRequest(
                "ride-1", "  passenger-1  ", money("950.00"), PaymentMethod.CARD));
        assertEquals("passenger-1", response.passengerId());
    }

    @Test void amountIsStoredAsBigDecimal() {
        PaymentResponse response = service.createPayment(request(PaymentMethod.CARD));
        assertEquals(money("950.00"), response.amount());
    }

    @Test void amountRoundsHalfUpToTwoPlaces() {
        PaymentResponse response = service.createPayment(new CreatePaymentRequest(
                "ride-1", "passenger-1", money("950.125"), PaymentMethod.CARD));
        assertEquals(money("950.13"), response.amount());
        assertEquals(2, response.amount().scale());
    }

    @Test void duplicateRidePaymentIsRejected() {
        when(repository.findByRideId("ride-1")).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
        assertThrows(DuplicatePaymentException.class,
                () -> service.createPayment(request(PaymentMethod.CARD)));
    }

    @Test void duplicateRidePaymentIsNotSaved() {
        when(repository.findByRideId("ride-1")).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
        assertThrows(DuplicatePaymentException.class,
                () -> service.createPayment(request(PaymentMethod.CARD)));
        verify(repository, never()).save(any());
    }

    @Test void concurrentUniqueIndexViolationMapsToDuplicatePayment() {
        when(repository.save(any(Payment.class))).thenThrow(new DuplicateKeyException("index detail"));
        assertThrows(DuplicatePaymentException.class,
                () -> service.createPayment(request(PaymentMethod.CARD)));
    }

    @Test void getByPaymentIdSucceeds() {
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
        assertEquals("payment-1", service.getPayment("payment-1").id());
    }

    @Test void getByPaymentIdTrimsIdentifier() {
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
        service.getPayment("  payment-1  ");
        verify(repository).findById("payment-1");
    }

    @Test void missingPaymentIdThrowsNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(PaymentNotFoundException.class, () -> service.getPayment("missing"));
    }

    @Test void getByRideIdSucceeds() {
        when(repository.findByRideId("ride-1")).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
        assertEquals("ride-1", service.getPaymentByRide("ride-1").rideId());
    }

    @Test void getByRideIdTrimsIdentifier() {
        when(repository.findByRideId("ride-1")).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
        service.getPaymentByRide("  ride-1  ");
        verify(repository).findByRideId("ride-1");
    }

    @Test void missingRidePaymentThrowsNotFound() {
        when(repository.findByRideId("missing")).thenReturn(Optional.empty());
        assertThrows(PaymentNotFoundException.class, () -> service.getPaymentByRide("missing"));
    }

    @Test void pendingPaymentCanBeCompleted() {
        Payment payment = payment(PaymentStatus.PENDING);
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment));
        assertEquals(PaymentStatus.COMPLETED, service.completePayment("payment-1").status());
        verify(repository).save(payment);
    }

    @Test void completionSetsCompletedAt() {
        Payment payment = payment(PaymentStatus.PENDING);
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment));
        assertTrue(service.completePayment("payment-1").completedAt()
                .isAfter(payment.getCreatedAt()));
    }

    @Test void completionChangesUpdatedAt() {
        Payment payment = payment(PaymentStatus.PENDING);
        LocalDateTime previous = payment.getUpdatedAt();
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment));
        assertTrue(service.completePayment("payment-1").updatedAt().isAfter(previous));
    }

    @Test void completedPaymentCannotBeCompletedAgain() {
        assertCompleteRejected(PaymentStatus.COMPLETED);
    }

    @Test void failedPaymentCannotBeCompleted() {
        assertCompleteRejected(PaymentStatus.FAILED);
    }

    @Test void pendingPaymentCanFail() {
        Payment payment = payment(PaymentStatus.PENDING);
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment));
        assertEquals(PaymentStatus.FAILED, service.failPayment("payment-1").status());
        verify(repository).save(payment);
    }

    @Test void failedPaymentKeepsCompletedAtNull() {
        Payment payment = payment(PaymentStatus.PENDING);
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment));
        assertNull(service.failPayment("payment-1").completedAt());
    }

    @Test void failureChangesUpdatedAt() {
        Payment payment = payment(PaymentStatus.PENDING);
        LocalDateTime previous = payment.getUpdatedAt();
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment));
        assertTrue(service.failPayment("payment-1").updatedAt().isAfter(previous));
    }

    @Test void completedPaymentCannotFail() {
        assertFailRejected(PaymentStatus.COMPLETED);
    }

    @Test void failedPaymentCannotFailAgain() {
        assertFailRejected(PaymentStatus.FAILED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void invalidRideIdIsRejected(String rideId) {
        CreatePaymentRequest request = new CreatePaymentRequest(rideId, "passenger-1",
                money("950.00"), PaymentMethod.CARD);
        assertThrows(InvalidPaymentRequestException.class, () -> service.createPayment(request));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "1000000.01"})
    void invalidAmountIsRejected(String amount) {
        CreatePaymentRequest request = new CreatePaymentRequest("ride-1", "passenger-1",
                money(amount), PaymentMethod.CARD);
        assertThrows(InvalidPaymentRequestException.class, () -> service.createPayment(request));
        verifyNoInteractions(repository);
    }

    @Test void missingAmountIsRejected() {
        CreatePaymentRequest request = new CreatePaymentRequest("ride-1", "passenger-1",
                null, PaymentMethod.CARD);
        assertThrows(InvalidPaymentRequestException.class, () -> service.createPayment(request));
    }

    @Test void missingMethodIsRejected() {
        CreatePaymentRequest request = new CreatePaymentRequest("ride-1", "passenger-1",
                money("950.00"), null);
        assertThrows(InvalidPaymentRequestException.class, () -> service.createPayment(request));
        verifyNoInteractions(repository);
    }

    @Test void nullRequestIsRejected() {
        assertThrows(InvalidPaymentRequestException.class, () -> service.createPayment(null));
        verifyNoInteractions(repository);
    }

    @Test void responseUsesRepositorySaveResult() {
        Payment saved = new Payment("database-id", "ride-1", "passenger-1", money("950.00"),
                "LKR", PaymentMethod.CARD, PaymentStatus.PENDING, LocalDateTime.now(),
                LocalDateTime.now(), null);
        when(repository.save(any(Payment.class))).thenReturn(saved);
        assertEquals("database-id", service.createPayment(request(PaymentMethod.CARD)).id());
    }

    private void assertCompleteRejected(PaymentStatus status) {
        Payment payment = payment(status);
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment));
        assertThrows(InvalidPaymentStateException.class,
                () -> service.completePayment("payment-1"));
        verify(repository, never()).save(any());
        assertSame(status, payment.getStatus());
    }

    private void assertFailRejected(PaymentStatus status) {
        Payment payment = payment(status);
        when(repository.findById("payment-1")).thenReturn(Optional.of(payment));
        assertThrows(InvalidPaymentStateException.class,
                () -> service.failPayment("payment-1"));
        verify(repository, never()).save(any());
        assertSame(status, payment.getStatus());
    }

    private CreatePaymentRequest request(PaymentMethod method) {
        return new CreatePaymentRequest("ride-1", "passenger-1", money("950.00"), method);
    }

    private Payment payment(PaymentStatus status) {
        LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
        LocalDateTime completedAt = status == PaymentStatus.COMPLETED
                ? createdAt.plusMinutes(30) : null;
        return new Payment("payment-1", "ride-1", "passenger-1", money("950.00"),
                "LKR", PaymentMethod.CARD, status, createdAt, createdAt, completedAt);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
