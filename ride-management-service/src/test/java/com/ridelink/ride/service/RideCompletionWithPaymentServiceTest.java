package com.ridelink.ride.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ridelink.ride.dto.CompleteRideWithPaymentRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.exception.FarePaymentServiceUnavailableException;
import com.ridelink.ride.exception.InvalidDriverServiceResponseException;
import com.ridelink.ride.exception.InvalidFarePaymentResponseException;
import com.ridelink.ride.exception.InvalidRideRequestException;
import com.ridelink.ride.exception.InvalidRideStateException;
import com.ridelink.ride.exception.PaymentConflictException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.integration.driver.DriverServiceClient;
import com.ridelink.ride.integration.farepayment.FarePaymentServiceClient;
import com.ridelink.ride.integration.farepayment.dto.FinalFareResponse;
import com.ridelink.ride.integration.farepayment.dto.PaymentMethod;
import com.ridelink.ride.integration.farepayment.dto.PaymentResponse;
import com.ridelink.ride.integration.farepayment.dto.PaymentStatus;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.repository.RideRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RideCompletionWithPaymentServiceTest {
    @Mock RideRepository rideRepository;
    @Mock DriverServiceClient driverServiceClient;
    @Mock FarePaymentServiceClient farePaymentServiceClient;
    private RideService rideService;

    @BeforeEach
    void setUp() {
        rideService = new RideService(rideRepository, driverServiceClient, farePaymentServiceClient);
    }

    @Test
    void inProgressRideCompletesSuccessfully() {
        Ride ride = successfulSetup(PaymentMethod.CARD);

        RideResponse response = rideService.completeRideWithPayment("ride-1", request(PaymentMethod.CARD));

        assertThat(response.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(response.finalFare()).isEqualByComparingTo("1150.00");
        assertThat(response.paymentId()).isEqualTo("payment-1");
        verify(rideRepository).save(ride);
    }

    @Test
    void finalFareUsesRideIdAndRequestedDistance() {
        successfulSetup(PaymentMethod.CARD);
        rideService.completeRideWithPayment("ride-1", request(PaymentMethod.CARD));
        verify(farePaymentServiceClient).obtainFinalFare("ride-1", money("12.50"));
    }

    @Test
    void paymentUsesRidePassengerAndFinalFareAmount() {
        successfulSetup(PaymentMethod.CARD);
        rideService.completeRideWithPayment("ride-1", request(PaymentMethod.CARD));
        verify(farePaymentServiceClient).obtainPendingPayment(
                "ride-1", "passenger-1", money("1150.00"), PaymentMethod.CARD);
    }

    @Test
    void cashPaymentMethodIsForwarded() {
        successfulSetup(PaymentMethod.CASH);
        rideService.completeRideWithPayment("ride-1", request(PaymentMethod.CASH));
        verify(farePaymentServiceClient).obtainPendingPayment(
                "ride-1", "passenger-1", money("1150.00"), PaymentMethod.CASH);
    }

    @Test
    void downstreamCallsAndDriverReleasePrecedeRideSave() {
        Ride ride = successfulSetup(PaymentMethod.CARD);
        rideService.completeRideWithPayment("ride-1", request(PaymentMethod.CARD));

        InOrder order = inOrder(farePaymentServiceClient, driverServiceClient, rideRepository);
        order.verify(farePaymentServiceClient).obtainFinalFare("ride-1", money("12.50"));
        order.verify(farePaymentServiceClient).obtainPendingPayment(
                "ride-1", "passenger-1", money("1150.00"), PaymentMethod.CARD);
        order.verify(driverServiceClient).markDriverAvailable("driver-1");
        order.verify(rideRepository).save(ride);
    }

    @Test
    void completionSetsTimestampsAndPreservesEstimatedFare() {
        Ride ride = successfulSetup(PaymentMethod.CARD);
        LocalDateTime previousUpdatedAt = ride.getUpdatedAt();

        RideResponse response = rideService.completeRideWithPayment("ride-1", request(PaymentMethod.CARD));

        assertThat(response.completedAt()).isAfter(previousUpdatedAt);
        assertThat(response.updatedAt()).isEqualTo(response.completedAt());
        assertThat(response.estimatedFare()).isEqualByComparingTo("900.00");
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {
            "REQUESTED", "ASSIGNED", "ACCEPTED", "COMPLETED", "CANCELLED"
    })
    void invalidRideStateIsRejectedWithoutDownstreamCalls(RideStatus status) {
        Ride ride = ride(status);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.completeRideWithPayment(
                "ride-1", request(PaymentMethod.CARD)))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessage("Ride must be IN_PROGRESS before completion with payment");

        verifyNoInteractions(farePaymentServiceClient, driverServiceClient);
        verify(rideRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void missingDriverIsRejectedWithoutDownstreamCalls(String driverId) {
        Ride ride = ride(RideStatus.IN_PROGRESS);
        ride.setDriverId(driverId);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.completeRideWithPayment(
                "ride-1", request(PaymentMethod.CARD)))
                .isInstanceOf(InvalidRideStateException.class);

        verifyNoInteractions(farePaymentServiceClient, driverServiceClient);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void missingRideReturns404StyleExceptionWithoutDownstreamCalls() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> rideService.completeRideWithPayment(
                "missing", request(PaymentMethod.CARD)))
                .isInstanceOf(RideNotFoundException.class);
        verifyNoInteractions(farePaymentServiceClient, driverServiceClient);
    }

    @Test
    void unavailableFareServiceLeavesRideInProgress() {
        Ride ride = ride(RideStatus.IN_PROGRESS);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(farePaymentServiceClient.obtainFinalFare("ride-1", money("12.50")))
                .thenThrow(new FarePaymentServiceUnavailableException());

        assertThatThrownBy(() -> rideService.completeRideWithPayment(
                "ride-1", request(PaymentMethod.CARD)))
                .isInstanceOf(FarePaymentServiceUnavailableException.class);

        assertRideNotCompleted(ride);
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    void malformedFareResponseLeavesRideInProgress() {
        Ride ride = ride(RideStatus.IN_PROGRESS);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(farePaymentServiceClient.obtainFinalFare("ride-1", money("12.50")))
                .thenThrow(new InvalidFarePaymentResponseException());

        assertThatThrownBy(() -> rideService.completeRideWithPayment(
                "ride-1", request(PaymentMethod.CARD)))
                .isInstanceOf(InvalidFarePaymentResponseException.class);

        assertRideNotCompleted(ride);
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    void unavailablePaymentServiceLeavesRideInProgressAndDriverUnavailable() {
        Ride ride = paymentFailureSetup(new FarePaymentServiceUnavailableException());
        assertThatThrownBy(() -> rideService.completeRideWithPayment(
                "ride-1", request(PaymentMethod.CARD)))
                .isInstanceOf(FarePaymentServiceUnavailableException.class);
        assertRideNotCompleted(ride);
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    void malformedPaymentResponseLeavesRideInProgressAndDriverUnavailable() {
        Ride ride = paymentFailureSetup(new InvalidFarePaymentResponseException());
        assertThatThrownBy(() -> rideService.completeRideWithPayment(
                "ride-1", request(PaymentMethod.CARD)))
                .isInstanceOf(InvalidFarePaymentResponseException.class);
        assertRideNotCompleted(ride);
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    void incompatibleExistingPaymentLeavesRideInProgress() {
        Ride ride = paymentFailureSetup(new PaymentConflictException());
        assertThatThrownBy(() -> rideService.completeRideWithPayment(
                "ride-1", request(PaymentMethod.CARD)))
                .isInstanceOf(PaymentConflictException.class);
        assertRideNotCompleted(ride);
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    void unavailableDriverReleaseLeavesRideInProgressAndUnsaved() {
        Ride ride = downstreamSuccessSetup(PaymentMethod.CARD);
        doThrow(new DriverServiceUnavailableException())
                .when(driverServiceClient).markDriverAvailable("driver-1");

        assertThatThrownBy(() -> rideService.completeRideWithPayment(
                "ride-1", request(PaymentMethod.CARD)))
                .isInstanceOf(DriverServiceUnavailableException.class);

        assertRideNotCompleted(ride);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void invalidDriverReleaseResponseLeavesRideInProgressAndUnsaved() {
        Ride ride = downstreamSuccessSetup(PaymentMethod.CARD);
        doThrow(new InvalidDriverServiceResponseException())
                .when(driverServiceClient).markDriverAvailable("driver-1");

        assertThatThrownBy(() -> rideService.completeRideWithPayment(
                "ride-1", request(PaymentMethod.CARD)))
                .isInstanceOf(InvalidDriverServiceResponseException.class);

        assertRideNotCompleted(ride);
        verify(rideRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1000.01"})
    void invalidDistanceIsRejectedBeforeRideLookup(String distance) {
        CompleteRideWithPaymentRequest request = new CompleteRideWithPaymentRequest(
                money(distance), PaymentMethod.CARD);
        assertThatThrownBy(() -> rideService.completeRideWithPayment("ride-1", request))
                .isInstanceOf(InvalidRideRequestException.class);
        verifyNoInteractions(rideRepository, farePaymentServiceClient, driverServiceClient);
    }

    @Test
    void missingDistanceIsRejectedBeforeRideLookup() {
        CompleteRideWithPaymentRequest request = new CompleteRideWithPaymentRequest(
                null, PaymentMethod.CARD);
        assertThatThrownBy(() -> rideService.completeRideWithPayment("ride-1", request))
                .isInstanceOf(InvalidRideRequestException.class);
        verifyNoInteractions(rideRepository, farePaymentServiceClient, driverServiceClient);
    }

    @Test
    void missingPaymentMethodIsRejectedBeforeRideLookup() {
        CompleteRideWithPaymentRequest request = new CompleteRideWithPaymentRequest(
                money("12.50"), null);
        assertThatThrownBy(() -> rideService.completeRideWithPayment("ride-1", request))
                .isInstanceOf(InvalidRideRequestException.class);
        verifyNoInteractions(rideRepository, farePaymentServiceClient, driverServiceClient);
    }

    private Ride successfulSetup(PaymentMethod method) {
        Ride ride = downstreamSuccessSetup(method);
        when(rideRepository.save(ride)).thenAnswer(call -> call.getArgument(0));
        return ride;
    }

    private Ride downstreamSuccessSetup(PaymentMethod method) {
        Ride ride = ride(RideStatus.IN_PROGRESS);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(farePaymentServiceClient.obtainFinalFare("ride-1", money("12.50")))
                .thenReturn(finalFare());
        when(farePaymentServiceClient.obtainPendingPayment(
                "ride-1", "passenger-1", money("1150.00"), method))
                .thenReturn(payment(method));
        return ride;
    }

    private Ride paymentFailureSetup(RuntimeException failure) {
        Ride ride = ride(RideStatus.IN_PROGRESS);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(farePaymentServiceClient.obtainFinalFare("ride-1", money("12.50")))
                .thenReturn(finalFare());
        when(farePaymentServiceClient.obtainPendingPayment(
                "ride-1", "passenger-1", money("1150.00"), PaymentMethod.CARD))
                .thenThrow(failure);
        return ride;
    }

    private void assertRideNotCompleted(Ride ride) {
        assertThat(ride.getStatus()).isEqualTo(RideStatus.IN_PROGRESS);
        assertThat(ride.getFinalFare()).isNull();
        assertThat(ride.getPaymentId()).isNull();
        assertThat(ride.getCompletedAt()).isNull();
        verify(rideRepository, never()).save(any());
    }

    private CompleteRideWithPaymentRequest request(PaymentMethod method) {
        return new CompleteRideWithPaymentRequest(money("12.50"), method);
    }

    private FinalFareResponse finalFare() {
        return new FinalFareResponse("fare-1", "ride-1", money("12.50"),
                money("1150.00"), "LKR");
    }

    private PaymentResponse payment(PaymentMethod method) {
        return new PaymentResponse("payment-1", "ride-1", "passenger-1",
                money("1150.00"), "LKR", method, PaymentStatus.PENDING);
    }

    private Ride ride(RideStatus status) {
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setPassengerId("passenger-1");
        ride.setPickupLocation("Colombo Fort");
        ride.setDestinationLocation("Bambalapitiya");
        ride.setServiceArea("Colombo");
        ride.setDriverId("driver-1");
        ride.setStatus(status);
        ride.setEstimatedFare(money("900.00"));
        ride.setRequestedAt(base.minusMinutes(30));
        ride.setAssignedAt(base.minusMinutes(20));
        ride.setAcceptedAt(base.minusMinutes(10));
        ride.setStartedAt(base);
        ride.setUpdatedAt(base);
        return ride;
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
