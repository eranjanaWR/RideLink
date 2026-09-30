package com.ridelink.ride.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.InvalidRideStateException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.integration.driver.DriverServiceClient;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RideLifecycleServiceTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverServiceClient driverServiceClient;

    @InjectMocks
    private RideService rideService;

    @BeforeEach
    void saveReturnsPersistedRide() {
        lenient().when(rideRepository.save(any(Ride.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void acceptRideTransitionsAssignedToAcceptedAndPreservesDriver() {
        Ride ride = rideWithStatus(RideStatus.ASSIGNED);
        LocalDateTime previousUpdatedAt = ride.getUpdatedAt();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        RideResponse response = rideService.acceptRide("ride-1");

        assertThat(response.status()).isEqualTo(RideStatus.ACCEPTED);
        assertThat(response.acceptedAt()).isNotNull();
        assertThat(response.updatedAt()).isEqualTo(response.acceptedAt()).isAfter(previousUpdatedAt);
        assertThat(response.driverId()).isEqualTo("driver-1");
        assertCommonFieldsPreserved(response, ride);
        verify(rideRepository).save(ride);
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    void acceptRideThrowsWhenRideDoesNotExist() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.acceptRide("missing"))
                .isInstanceOf(RideNotFoundException.class);
        verify(rideRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {
            "REQUESTED", "ACCEPTED", "IN_PROGRESS", "COMPLETED", "CANCELLED"
    })
    void acceptRideRejectsEveryStatusExceptAssigned(RideStatus status) {
        Ride ride = rideWithStatus(status);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.acceptRide("ride-1"))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessage("Ride must be ASSIGNED before it can be accepted");
        verify(rideRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void acceptRideRejectsAssignedRideWithoutUsableDriverId(String driverId) {
        Ride ride = rideWithStatus(RideStatus.ASSIGNED);
        ride.setDriverId(driverId);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.acceptRide("ride-1"))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessage("Ride must have an assigned driver before it can be accepted");
        verify(rideRepository, never()).save(any());
    }

    @Test
    void startRideTransitionsAcceptedToInProgressAndPreservesAcceptedTimestamp() {
        Ride ride = rideWithStatus(RideStatus.ACCEPTED);
        LocalDateTime acceptedAt = ride.getAcceptedAt();
        LocalDateTime previousUpdatedAt = ride.getUpdatedAt();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        RideResponse response = rideService.startRide("ride-1");

        assertThat(response.status()).isEqualTo(RideStatus.IN_PROGRESS);
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.updatedAt()).isEqualTo(response.startedAt()).isAfter(previousUpdatedAt);
        assertThat(response.acceptedAt()).isEqualTo(acceptedAt);
        assertCommonFieldsPreserved(response, ride);
        verify(rideRepository).save(ride);
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    void startRideThrowsWhenRideDoesNotExist() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.startRide("missing"))
                .isInstanceOf(RideNotFoundException.class);
        verify(rideRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {
            "REQUESTED", "ASSIGNED", "IN_PROGRESS", "COMPLETED", "CANCELLED"
    })
    void startRideRejectsEveryStatusExceptAccepted(RideStatus status) {
        Ride ride = rideWithStatus(status);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.startRide("ride-1"))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessage("Ride must be ACCEPTED before it can be started");
        verify(rideRepository, never()).save(any());
    }

    @Test
    void completeRideTransitionsInProgressToCompletedAndPreservesPriorData() {
        Ride ride = rideWithStatus(RideStatus.IN_PROGRESS);
        LocalDateTime requestedAt = ride.getRequestedAt();
        LocalDateTime assignedAt = ride.getAssignedAt();
        LocalDateTime acceptedAt = ride.getAcceptedAt();
        LocalDateTime startedAt = ride.getStartedAt();
        LocalDateTime previousUpdatedAt = ride.getUpdatedAt();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        RideResponse response = rideService.completeRide("ride-1");

        assertThat(response.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
        assertThat(response.updatedAt()).isEqualTo(response.completedAt()).isAfter(previousUpdatedAt);
        assertThat(response.requestedAt()).isEqualTo(requestedAt);
        assertThat(response.assignedAt()).isEqualTo(assignedAt);
        assertThat(response.acceptedAt()).isEqualTo(acceptedAt);
        assertThat(response.startedAt()).isEqualTo(startedAt);
        assertThat(response.driverId()).isEqualTo("driver-1");
        assertThat(response.estimatedFare()).isEqualByComparingTo("450.00");
        assertThat(response.finalFare()).isEqualByComparingTo("475.00");
        assertCommonFieldsPreserved(response, ride);
        verify(driverServiceClient).markDriverAvailable("driver-1");
        verify(rideRepository).save(ride);
    }

    @Test
    void completeRideThrowsWhenRideDoesNotExist() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.completeRide("missing"))
                .isInstanceOf(RideNotFoundException.class);
        verify(rideRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {
            "REQUESTED", "ASSIGNED", "ACCEPTED", "COMPLETED", "CANCELLED"
    })
    void completeRideRejectsEveryStatusExceptInProgress(RideStatus status) {
        Ride ride = rideWithStatus(status);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.completeRide("ride-1"))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessage("Ride must be IN_PROGRESS before it can be completed");
        verifyNoInteractions(driverServiceClient);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void cancelRideTransitionsRequestedToCancelled() {
        Ride ride = rideWithStatus(RideStatus.REQUESTED);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        RideResponse response = rideService.cancelRide("ride-1");

        assertThat(response.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(response.cancelledAt()).isNotNull();
        assertThat(response.updatedAt()).isEqualTo(response.cancelledAt());
        verify(rideRepository).save(ride);
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    void cancelRideTransitionsAssignedAndPreservesDriverAndAssignedTimestamp() {
        Ride ride = rideWithStatus(RideStatus.ASSIGNED);
        LocalDateTime assignedAt = ride.getAssignedAt();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        RideResponse response = rideService.cancelRide("ride-1");

        assertThat(response.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(response.driverId()).isEqualTo("driver-1");
        assertThat(response.assignedAt()).isEqualTo(assignedAt);
        assertThat(response.cancelledAt()).isNotNull();
        verify(driverServiceClient).markDriverAvailable("driver-1");
        verify(rideRepository).save(ride);
    }

    @Test
    void cancelRideTransitionsAcceptedAndPreservesEarlierLifecycleTimestamps() {
        Ride ride = rideWithStatus(RideStatus.ACCEPTED);
        LocalDateTime assignedAt = ride.getAssignedAt();
        LocalDateTime acceptedAt = ride.getAcceptedAt();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        RideResponse response = rideService.cancelRide("ride-1");

        assertThat(response.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(response.driverId()).isEqualTo("driver-1");
        assertThat(response.assignedAt()).isEqualTo(assignedAt);
        assertThat(response.acceptedAt()).isEqualTo(acceptedAt);
        assertThat(response.cancelledAt()).isNotNull();
        assertThat(response.estimatedFare()).isEqualByComparingTo("450.00");
        assertThat(response.finalFare()).isEqualByComparingTo("475.00");
        verify(driverServiceClient).markDriverAvailable("driver-1");
        verify(rideRepository).save(ride);
    }

    @Test
    void cancelRideThrowsWhenRideDoesNotExist() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.cancelRide("missing"))
                .isInstanceOf(RideNotFoundException.class);
        verify(rideRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void cancelRideRejectsTerminalOrActiveRideStates(RideStatus status) {
        Ride ride = rideWithStatus(status);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancelRide("ride-1"))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessage("Ride cannot be cancelled from status " + status);
        verifyNoInteractions(driverServiceClient);
        verify(rideRepository, never()).save(any());
    }

    private Ride rideWithStatus(RideStatus status) {
        LocalDateTime base = LocalDateTime.now().minusHours(2);
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setPassengerId("account-1");
        ride.setPickupLocation("Colombo Fort");
        ride.setDestinationLocation("Bambalapitiya");
        ride.setServiceArea("Colombo");
        ride.setStatus(status);
        ride.setEstimatedFare(new BigDecimal("450.00"));
        ride.setFinalFare(new BigDecimal("475.00"));
        ride.setRequestedAt(base);
        ride.setUpdatedAt(base);

        if (status != RideStatus.REQUESTED) {
            ride.setDriverId("driver-1");
            ride.setAssignedAt(base.plusMinutes(10));
        }
        if (status == RideStatus.ACCEPTED
                || status == RideStatus.IN_PROGRESS
                || status == RideStatus.COMPLETED) {
            ride.setAcceptedAt(base.plusMinutes(20));
        }
        if (status == RideStatus.IN_PROGRESS || status == RideStatus.COMPLETED) {
            ride.setStartedAt(base.plusMinutes(30));
        }
        if (status == RideStatus.COMPLETED) {
            ride.setCompletedAt(base.plusMinutes(60));
        }
        if (status == RideStatus.CANCELLED) {
            ride.setCancelledAt(base.plusMinutes(30));
        }
        return ride;
    }

    private void assertCommonFieldsPreserved(RideResponse response, Ride source) {
        assertThat(response.passengerId()).isEqualTo(source.getPassengerId());
        assertThat(response.pickupLocation()).isEqualTo(source.getPickupLocation());
        assertThat(response.destinationLocation()).isEqualTo(source.getDestinationLocation());
        assertThat(response.serviceArea()).isEqualTo(source.getServiceArea());
        assertThat(response.requestedAt()).isEqualTo(source.getRequestedAt());
        assertThat(response.estimatedFare()).isEqualByComparingTo(source.getEstimatedFare());
        assertThat(response.finalFare()).isEqualByComparingTo(source.getFinalFare());
    }
}
