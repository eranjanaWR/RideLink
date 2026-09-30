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

import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.exception.InvalidDriverServiceResponseException;
import com.ridelink.ride.exception.InvalidRideStateException;
import com.ridelink.ride.integration.driver.DriverServiceClient;
import com.ridelink.ride.integration.driver.dto.EligibleDriverResponse;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.repository.RideRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DriverAvailabilitySynchronizationServiceTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverServiceClient driverServiceClient;

    @InjectMocks
    private RideService rideService;

    @Test
    void assignmentMarksSelectedDriverUnavailableBeforeSavingAssignedRide() {
        Ride ride = rideWithStatus(RideStatus.REQUESTED);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo"))
                .thenReturn(List.of(eligibleDriver("driver-2"), eligibleDriver("driver-1")));
        when(rideRepository.save(ride)).thenAnswer(invocation -> invocation.getArgument(0));

        rideService.assignDriver("ride-1");

        InOrder order = inOrder(driverServiceClient, rideRepository);
        order.verify(driverServiceClient).markDriverUnavailable("driver-1");
        order.verify(rideRepository).save(ride);
        assertThat(ride.getStatus()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(ride.getDriverId()).isEqualTo("driver-1");
    }

    @Test
    void assignmentAvailabilityFailureLeavesRideRequestedAndUnsaved() {
        Ride ride = rideWithStatus(RideStatus.REQUESTED);
        LocalDateTime originalUpdatedAt = ride.getUpdatedAt();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo"))
                .thenReturn(List.of(eligibleDriver("driver-1")));
        doThrow(new DriverServiceUnavailableException())
                .when(driverServiceClient).markDriverUnavailable("driver-1");

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isInstanceOf(DriverServiceUnavailableException.class);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.REQUESTED);
        assertThat(ride.getDriverId()).isNull();
        assertThat(ride.getAssignedAt()).isNull();
        assertThat(ride.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        verify(rideRepository, never()).save(any());
        verify(driverServiceClient, never()).markDriverAvailable(any());
    }

    @Test
    void malformedAssignmentAvailabilityResponseLeavesRideRequestedAndUnsaved() {
        Ride ride = rideWithStatus(RideStatus.REQUESTED);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo"))
                .thenReturn(List.of(eligibleDriver("driver-1")));
        doThrow(new InvalidDriverServiceResponseException())
                .when(driverServiceClient).markDriverUnavailable("driver-1");

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.REQUESTED);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void assignmentPersistenceFailureRestoresRideAndCompensatesAvailability() {
        Ride ride = rideWithStatus(RideStatus.REQUESTED);
        LocalDateTime originalUpdatedAt = ride.getUpdatedAt();
        IllegalStateException persistenceFailure = new IllegalStateException("persistence failed");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo"))
                .thenReturn(List.of(eligibleDriver("driver-1")));
        when(rideRepository.save(ride)).thenThrow(persistenceFailure);

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isSameAs(persistenceFailure);

        verify(driverServiceClient).markDriverUnavailable("driver-1");
        verify(driverServiceClient).markDriverAvailable("driver-1");
        assertThat(ride.getStatus()).isEqualTo(RideStatus.REQUESTED);
        assertThat(ride.getDriverId()).isNull();
        assertThat(ride.getAssignedAt()).isNull();
        assertThat(ride.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void compensationFailureDoesNotReplaceOriginalPersistenceFailure() {
        Ride ride = rideWithStatus(RideStatus.REQUESTED);
        IllegalStateException persistenceFailure = new IllegalStateException("persistence failed");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo"))
                .thenReturn(List.of(eligibleDriver("driver-1")));
        when(rideRepository.save(ride)).thenThrow(persistenceFailure);
        doThrow(new DriverServiceUnavailableException())
                .when(driverServiceClient).markDriverAvailable("driver-1");

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isSameAs(persistenceFailure);

        verify(driverServiceClient).markDriverAvailable("driver-1");
        assertThat(ride.getStatus()).isEqualTo(RideStatus.REQUESTED);
    }

    @Test
    void completionMarksDriverAvailableBeforeSavingCompletedRide() {
        Ride ride = rideWithStatus(RideStatus.IN_PROGRESS);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenAnswer(invocation -> invocation.getArgument(0));

        rideService.completeRide("ride-1");

        InOrder order = inOrder(driverServiceClient, rideRepository);
        order.verify(driverServiceClient).markDriverAvailable("driver-1");
        order.verify(rideRepository).save(ride);
        assertThat(ride.getStatus()).isEqualTo(RideStatus.COMPLETED);
    }

    @Test
    void completionAvailabilityFailureLeavesRideInProgressAndUnsaved() {
        Ride ride = rideWithStatus(RideStatus.IN_PROGRESS);
        LocalDateTime originalUpdatedAt = ride.getUpdatedAt();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        doThrow(new DriverServiceUnavailableException())
                .when(driverServiceClient).markDriverAvailable("driver-1");

        assertThatThrownBy(() -> rideService.completeRide("ride-1"))
                .isInstanceOf(DriverServiceUnavailableException.class);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.IN_PROGRESS);
        assertThat(ride.getCompletedAt()).isNull();
        assertThat(ride.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void malformedCompletionAvailabilityResponseLeavesRideInProgressAndUnsaved() {
        Ride ride = rideWithStatus(RideStatus.IN_PROGRESS);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        doThrow(new InvalidDriverServiceResponseException())
                .when(driverServiceClient).markDriverAvailable("driver-1");

        assertThatThrownBy(() -> rideService.completeRide("ride-1"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.IN_PROGRESS);
        verify(rideRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void completionRejectsMissingDriverWithoutCallingDriverService(String driverId) {
        Ride ride = rideWithStatus(RideStatus.IN_PROGRESS);
        ride.setDriverId(driverId);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.completeRide("ride-1"))
                .isInstanceOf(InvalidRideStateException.class);

        verifyNoInteractions(driverServiceClient);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void requestedCancellationDoesNotCallDriverService() {
        Ride ride = rideWithStatus(RideStatus.REQUESTED);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenAnswer(invocation -> invocation.getArgument(0));

        rideService.cancelRide("ride-1");

        verifyNoInteractions(driverServiceClient);
        assertThat(ride.getStatus()).isEqualTo(RideStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"ASSIGNED", "ACCEPTED"})
    void assignedCancellationMarksDriverAvailableBeforeSaving(RideStatus initialStatus) {
        Ride ride = rideWithStatus(initialStatus);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenAnswer(invocation -> invocation.getArgument(0));

        rideService.cancelRide("ride-1");

        InOrder order = inOrder(driverServiceClient, rideRepository);
        order.verify(driverServiceClient).markDriverAvailable("driver-1");
        order.verify(rideRepository).save(ride);
        assertThat(ride.getStatus()).isEqualTo(RideStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"ASSIGNED", "ACCEPTED"})
    void assignedCancellationAvailabilityFailurePreservesStateAndDoesNotSave(RideStatus initialStatus) {
        Ride ride = rideWithStatus(initialStatus);
        LocalDateTime originalUpdatedAt = ride.getUpdatedAt();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        doThrow(new DriverServiceUnavailableException())
                .when(driverServiceClient).markDriverAvailable("driver-1");

        assertThatThrownBy(() -> rideService.cancelRide("ride-1"))
                .isInstanceOf(DriverServiceUnavailableException.class);

        assertThat(ride.getStatus()).isEqualTo(initialStatus);
        assertThat(ride.getCancelledAt()).isNull();
        assertThat(ride.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void assignedCancellationRejectsBlankDriverWithoutCallingDriverService() {
        Ride ride = rideWithStatus(RideStatus.ASSIGNED);
        ride.setDriverId("  ");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancelRide("ride-1"))
                .isInstanceOf(InvalidRideStateException.class);

        verifyNoInteractions(driverServiceClient);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void acceptedCancellationRejectsNullDriverWithoutCallingDriverService() {
        Ride ride = rideWithStatus(RideStatus.ACCEPTED);
        ride.setDriverId(null);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancelRide("ride-1"))
                .isInstanceOf(InvalidRideStateException.class);

        verifyNoInteractions(driverServiceClient);
        verify(rideRepository, never()).save(any());
    }

    private Ride rideWithStatus(RideStatus status) {
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setPassengerId("account-1");
        ride.setPickupLocation("Colombo Fort");
        ride.setDestinationLocation("Bambalapitiya");
        ride.setServiceArea("Colombo");
        ride.setStatus(status);
        ride.setRequestedAt(base);
        ride.setUpdatedAt(base);
        if (status != RideStatus.REQUESTED) {
            ride.setDriverId("driver-1");
            ride.setAssignedAt(base.plusMinutes(10));
        }
        if (status == RideStatus.ACCEPTED || status == RideStatus.IN_PROGRESS) {
            ride.setAcceptedAt(base.plusMinutes(20));
        }
        if (status == RideStatus.IN_PROGRESS) {
            ride.setStartedAt(base.plusMinutes(30));
        }
        return ride;
    }

    private EligibleDriverResponse eligibleDriver(String driverId) {
        return new EligibleDriverResponse(
                driverId,
                "account-driver",
                "Colombo",
                6.9271,
                79.8612,
                "AVAILABLE",
                "vehicle-1",
                "TEST-CAB-001",
                "CAR"
        );
    }
}
