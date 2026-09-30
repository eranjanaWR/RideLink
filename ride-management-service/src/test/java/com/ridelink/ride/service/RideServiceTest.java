package com.ridelink.ride.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.InvalidRideRequestException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.repository.RideRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock
    private RideRepository rideRepository;

    @InjectMocks
    private RideService rideService;

    @Test
    void createRideInitializesRequiredStateAndTimestamps() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        RideResponse response = rideService.createRide(validRequest());
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.id()).isNotBlank();
        assertThat(UUID.fromString(response.id())).isNotNull();
        assertThat(response.status()).isEqualTo(RideStatus.REQUESTED);
        assertThat(response.driverId()).isNull();
        assertThat(response.estimatedFare()).isNull();
        assertThat(response.finalFare()).isNull();
        assertThat(response.requestedAt()).isBetween(before, after);
        assertThat(response.updatedAt()).isEqualTo(response.requestedAt());
        assertThat(response.assignedAt()).isNull();
        assertThat(response.acceptedAt()).isNull();
        assertThat(response.startedAt()).isNull();
        assertThat(response.completedAt()).isNull();
        assertThat(response.cancelledAt()).isNull();
    }

    @Test
    void createRideTrimsAllUserProvidedValues() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideResponse response = rideService.createRide(new CreateRideRequest(
                "  account-1  ",
                "  Colombo Fort  ",
                "  Bambalapitiya  ",
                "  Colombo  "
        ));

        assertThat(response.passengerId()).isEqualTo("account-1");
        assertThat(response.pickupLocation()).isEqualTo("Colombo Fort");
        assertThat(response.destinationLocation()).isEqualTo("Bambalapitiya");
        assertThat(response.serviceArea()).isEqualTo("Colombo");
    }

    @Test
    void createRideSavesExactlyOnce() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rideService.createRide(validRequest());

        ArgumentCaptor<Ride> captor = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(captor.capture());
        assertThat(captor.getValue().getPassengerId()).isEqualTo("account-passenger-001");
    }

    @Test
    void createRideRejectsIdenticalLocations() {
        CreateRideRequest request = requestWithLocations("Colombo Fort", "Colombo Fort");

        assertThatThrownBy(() -> rideService.createRide(request))
                .isInstanceOf(InvalidRideRequestException.class)
                .hasMessageContaining("must be different");
        verify(rideRepository, never()).save(any());
    }

    @Test
    void createRideRejectsLocationsThatDifferOnlyByCase() {
        CreateRideRequest request = requestWithLocations("Colombo Fort", "colombo fort");

        assertThatThrownBy(() -> rideService.createRide(request))
                .isInstanceOf(InvalidRideRequestException.class);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void createRideRejectsLocationsThatMatchAfterTrimming() {
        CreateRideRequest request = requestWithLocations("  Colombo Fort ", "Colombo Fort  ");

        assertThatThrownBy(() -> rideService.createRide(request))
                .isInstanceOf(InvalidRideRequestException.class);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void getRideByIdReturnsMappedRide() {
        Ride ride = requestedRide("ride-1", "account-1");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        RideResponse response = rideService.getRideById("ride-1");

        assertThat(response.id()).isEqualTo("ride-1");
        assertThat(response.passengerId()).isEqualTo("account-1");
        assertThat(response.status()).isEqualTo(RideStatus.REQUESTED);
    }

    @Test
    void getRideByIdTrimsIdentifier() {
        Ride ride = requestedRide("ride-1", "account-1");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        rideService.getRideById("  ride-1  ");

        verify(rideRepository).findById("ride-1");
    }

    @Test
    void getRideByIdThrowsWhenRideDoesNotExist() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.getRideById("missing"))
                .isInstanceOf(RideNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void getRideByIdRejectsBlankIdentifier() {
        assertThatThrownBy(() -> rideService.getRideById("  "))
                .isInstanceOf(InvalidRideRequestException.class);
    }

    @Test
    void getRideByIdRejectsNullIdentifier() {
        assertThatThrownBy(() -> rideService.getRideById(null))
                .isInstanceOf(InvalidRideRequestException.class);
    }

    @Test
    void getRidesByPassengerIdReturnsMappedRides() {
        when(rideRepository.findAllByPassengerId("account-1"))
                .thenReturn(List.of(requestedRide("ride-1", "account-1"), requestedRide("ride-2", "account-1")));

        List<RideResponse> responses = rideService.getRidesByPassengerId("account-1");

        assertThat(responses).extracting(RideResponse::id).containsExactly("ride-1", "ride-2");
    }

    @Test
    void getRidesByPassengerIdReturnsEmptyList() {
        when(rideRepository.findAllByPassengerId("account-1")).thenReturn(List.of());

        List<RideResponse> responses = rideService.getRidesByPassengerId("account-1");

        assertThat(responses).isEmpty();
    }

    @Test
    void getRidesByPassengerIdTrimsIdentifier() {
        when(rideRepository.findAllByPassengerId("account-1")).thenReturn(List.of());

        rideService.getRidesByPassengerId("  account-1  ");

        verify(rideRepository).findAllByPassengerId("account-1");
    }

    @Test
    void getRidesByPassengerIdRejectsBlankIdentifier() {
        assertThatThrownBy(() -> rideService.getRidesByPassengerId("  "))
                .isInstanceOf(InvalidRideRequestException.class)
                .hasMessageContaining("passengerId");
    }

    @Test
    void getRidesByPassengerIdRejectsNullIdentifier() {
        assertThatThrownBy(() -> rideService.getRidesByPassengerId(null))
                .isInstanceOf(InvalidRideRequestException.class);
    }

    @Test
    void responseMappingPreservesMoneyAndLifecycleFields() {
        Ride ride = requestedRide("ride-1", "account-1");
        LocalDateTime now = LocalDateTime.now();
        ride.setStatus(RideStatus.COMPLETED);
        ride.setDriverId("driver-1");
        ride.setEstimatedFare(new BigDecimal("500.00"));
        ride.setFinalFare(new BigDecimal("525.50"));
        ride.setAssignedAt(now.minusMinutes(20));
        ride.setAcceptedAt(now.minusMinutes(18));
        ride.setStartedAt(now.minusMinutes(15));
        ride.setCompletedAt(now.minusMinutes(1));
        ride.setUpdatedAt(now);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        RideResponse response = rideService.getRideById("ride-1");

        assertThat(response.driverId()).isEqualTo("driver-1");
        assertThat(response.estimatedFare()).isEqualByComparingTo("500.00");
        assertThat(response.finalFare()).isEqualByComparingTo("525.50");
        assertThat(response.assignedAt()).isEqualTo(now.minusMinutes(20));
        assertThat(response.acceptedAt()).isEqualTo(now.minusMinutes(18));
        assertThat(response.startedAt()).isEqualTo(now.minusMinutes(15));
        assertThat(response.completedAt()).isEqualTo(now.minusMinutes(1));
        assertThat(response.updatedAt()).isEqualTo(now);
    }

    private CreateRideRequest validRequest() {
        return requestWithLocations("Colombo Fort", "Bambalapitiya");
    }

    private CreateRideRequest requestWithLocations(String pickup, String destination) {
        return new CreateRideRequest("account-passenger-001", pickup, destination, "Colombo");
    }

    private Ride requestedRide(String id, String passengerId) {
        LocalDateTime now = LocalDateTime.now();
        Ride ride = new Ride();
        ride.setId(id);
        ride.setPassengerId(passengerId);
        ride.setPickupLocation("Colombo Fort");
        ride.setDestinationLocation("Bambalapitiya");
        ride.setServiceArea("Colombo");
        ride.setStatus(RideStatus.REQUESTED);
        ride.setRequestedAt(now);
        ride.setUpdatedAt(now);
        return ride;
    }
}
