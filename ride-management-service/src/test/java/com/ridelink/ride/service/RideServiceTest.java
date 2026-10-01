package com.ridelink.ride.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.exception.InvalidDriverServiceResponseException;
import com.ridelink.ride.exception.InvalidRideRequestException;
import com.ridelink.ride.exception.InvalidRideStateException;
import com.ridelink.ride.exception.NoEligibleDriverException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.integration.driver.DriverServiceClient;
import com.ridelink.ride.integration.driver.dto.EligibleDriverResponse;
import com.ridelink.ride.integration.farepayment.FarePaymentServiceClient;
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

    @Mock
    private DriverServiceClient driverServiceClient;

    @Mock
    private FarePaymentServiceClient farePaymentServiceClient;

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

    @Test
    void assignDriverUpdatesRequestedRideAndSavesIt() {
        Ride ride = requestedRide("ride-1", "account-1");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo")).thenReturn(List.of(driver("driver-2")));
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        RideResponse response = rideService.assignDriver("ride-1");
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.driverId()).isEqualTo("driver-2");
        assertThat(response.status()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(response.assignedAt()).isBetween(before, after);
        assertThat(response.updatedAt()).isEqualTo(response.assignedAt());
        verify(driverServiceClient).getEligibleDrivers("Colombo");
        verify(driverServiceClient).markDriverUnavailable("driver-2");
        verify(rideRepository).save(ride);
    }

    @Test
    void assignDriverPreservesExistingRideDetails() {
        Ride ride = requestedRide("ride-1", "account-1");
        LocalDateTime requestedAt = ride.getRequestedAt();
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo")).thenReturn(List.of(driver("driver-1")));
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideResponse response = rideService.assignDriver("ride-1");

        assertThat(response.passengerId()).isEqualTo("account-1");
        assertThat(response.pickupLocation()).isEqualTo("Colombo Fort");
        assertThat(response.destinationLocation()).isEqualTo("Bambalapitiya");
        assertThat(response.serviceArea()).isEqualTo("Colombo");
        assertThat(response.requestedAt()).isEqualTo(requestedAt);
        assertThat(response.estimatedFare()).isNull();
        assertThat(response.finalFare()).isNull();
    }

    @Test
    void assignDriverThrowsWhenRideDoesNotExist() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.assignDriver("missing"))
                .isInstanceOf(RideNotFoundException.class);
        verifyNoInteractions(driverServiceClient);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void assignDriverThrowsWhenNoEligibleDriverExists() {
        Ride ride = requestedRide("ride-1", "account-1");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo")).thenReturn(List.of());

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isInstanceOf(NoEligibleDriverException.class)
                .hasMessageContaining("Colombo");
        verify(driverServiceClient, never()).markDriverUnavailable(any());
        verify(rideRepository, never()).save(any());
    }

    @Test
    void assignDriverRejectsAssignedRide() {
        assertAssignmentRejectedForStatus(RideStatus.ASSIGNED);
    }

    @Test
    void assignDriverRejectsAcceptedRide() {
        assertAssignmentRejectedForStatus(RideStatus.ACCEPTED);
    }

    @Test
    void assignDriverRejectsInProgressRide() {
        assertAssignmentRejectedForStatus(RideStatus.IN_PROGRESS);
    }

    @Test
    void assignDriverRejectsCompletedRide() {
        assertAssignmentRejectedForStatus(RideStatus.COMPLETED);
    }

    @Test
    void assignDriverRejectsCancelledRide() {
        assertAssignmentRejectedForStatus(RideStatus.CANCELLED);
    }

    @Test
    void assignDriverSelectsLowestDriverIdDeterministically() {
        Ride ride = requestedRide("ride-1", "account-1");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo"))
                .thenReturn(List.of(driver("driver-z"), driver("driver-a"), driver("driver-m")));
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideResponse response = rideService.assignDriver("ride-1");

        assertThat(response.driverId()).isEqualTo("driver-a");
    }

    @Test
    void assignDriverRejectsBlankSelectedDriverId() {
        Ride ride = requestedRide("ride-1", "account-1");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo")).thenReturn(List.of(driver(" ")));

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);
        verify(driverServiceClient, never()).markDriverUnavailable(any());
        verify(rideRepository, never()).save(any());
    }

    @Test
    void assignDriverRejectsNullSelectedDriverId() {
        Ride ride = requestedRide("ride-1", "account-1");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo")).thenReturn(List.of(driver(null)));

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);
        verify(driverServiceClient, never()).markDriverUnavailable(any());
        verify(rideRepository, never()).save(any());
    }

    @Test
    void assignDriverRejectsNullEntryInUpstreamResponse() {
        Ride ride = requestedRide("ride-1", "account-1");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo"))
                .thenReturn(java.util.Arrays.asList((EligibleDriverResponse) null));

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void assignDriverPropagatesStableUnavailableExceptionWithoutSaving() {
        Ride ride = requestedRide("ride-1", "account-1");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo"))
                .thenThrow(new DriverServiceUnavailableException());

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isInstanceOf(DriverServiceUnavailableException.class)
                .hasMessage("Driver & Vehicle Service is currently unavailable");
        verify(rideRepository, never()).save(any());
    }

    @Test
    void assignDriverRejectsNullDriverList() {
        Ride ride = requestedRide("ride-1", "account-1");
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        when(driverServiceClient.getEligibleDrivers("Colombo")).thenReturn(null);

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);
        verify(rideRepository, never()).save(any());
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

    private EligibleDriverResponse driver(String driverId) {
        return new EligibleDriverResponse(
                driverId,
                "driver-account",
                "Colombo",
                6.9271,
                79.8612,
                "AVAILABLE",
                "vehicle-1",
                "TEST-CAB-001",
                "CAR"
        );
    }

    private void assertAssignmentRejectedForStatus(RideStatus status) {
        Ride ride = requestedRide("ride-1", "account-1");
        ride.setStatus(status);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.assignDriver("ride-1"))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessageContaining(status.name());
        verifyNoInteractions(driverServiceClient);
        verify(rideRepository, never()).save(any());
    }
}
