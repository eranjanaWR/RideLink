package com.ridelink.drivervehicle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ridelink.drivervehicle.dto.EligibleDriverResponse;
import com.ridelink.drivervehicle.exception.InvalidServiceAreaException;
import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;
import com.ridelink.drivervehicle.model.DriverProfile;
import com.ridelink.drivervehicle.model.Vehicle;
import com.ridelink.drivervehicle.model.VehicleType;
import com.ridelink.drivervehicle.repository.DriverProfileRepository;
import com.ridelink.drivervehicle.repository.VehicleRepository;

@ExtendWith(MockitoExtension.class)
class EligibleDriverServiceTest {

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    private EligibleDriverService service;

    @BeforeEach
    void setUp() {
        service = new EligibleDriverService(driverProfileRepository, vehicleRepository);
    }

    @Test
    void eligibleDriverWithMatchingAreaLocationAndVehicleIsReturned() {
        DriverProfile driver = eligibleDriver("driver-1", "account-1", "Colombo");
        when(driverProfileRepository.findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "Colombo"))
                .thenReturn(List.of(driver));
        when(vehicleRepository.findAllByDriverId("driver-1"))
                .thenReturn(List.of(vehicle("vehicle-1", "driver-1", "CAB-1234")));

        List<EligibleDriverResponse> responses = service.findEligibleDrivers("Colombo");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().driverId()).isEqualTo("driver-1");
    }

    @Test
    void unavailableDriverIsNotReturned() {
        DriverProfile driver = eligibleDriver("driver-1", "account-1", "Colombo");
        driver.setAvailabilityStatus(DriverAvailabilityStatus.UNAVAILABLE);
        when(driverProfileRepository.findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "Colombo"))
                .thenReturn(List.of(driver));

        List<EligibleDriverResponse> responses = service.findEligibleDrivers("Colombo");

        assertThat(responses).isEmpty();
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void driverFromDifferentServiceAreaIsNotReturned() {
        DriverProfile driver = eligibleDriver("driver-1", "account-1", "Kandy");
        when(driverProfileRepository.findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "Colombo"))
                .thenReturn(List.of(driver));

        List<EligibleDriverResponse> responses = service.findEligibleDrivers("Colombo");

        assertThat(responses).isEmpty();
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void serviceAreaMatchingTrimsInputAndIgnoresCase() {
        DriverProfile driver = eligibleDriver("driver-1", "account-1", "Colombo");
        when(driverProfileRepository.findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "cOlOmBo"))
                .thenReturn(List.of(driver));
        when(vehicleRepository.findAllByDriverId("driver-1"))
                .thenReturn(List.of(vehicle("vehicle-1", "driver-1", "CAB-1234")));

        List<EligibleDriverResponse> responses = service.findEligibleDrivers("  cOlOmBo  ");

        assertThat(responses).hasSize(1);
        verify(driverProfileRepository).findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "cOlOmBo");
    }

    @Test
    void driverWithoutLatitudeIsExcluded() {
        DriverProfile driver = eligibleDriver("driver-1", "account-1", "Colombo");
        driver.setLatitude(null);
        when(driverProfileRepository.findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "Colombo"))
                .thenReturn(List.of(driver));

        List<EligibleDriverResponse> responses = service.findEligibleDrivers("Colombo");

        assertThat(responses).isEmpty();
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void driverWithoutLongitudeIsExcluded() {
        DriverProfile driver = eligibleDriver("driver-1", "account-1", "Colombo");
        driver.setLongitude(null);
        when(driverProfileRepository.findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "Colombo"))
                .thenReturn(List.of(driver));

        List<EligibleDriverResponse> responses = service.findEligibleDrivers("Colombo");

        assertThat(responses).isEmpty();
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void driverWithoutRegisteredVehicleIsExcluded() {
        DriverProfile driver = eligibleDriver("driver-1", "account-1", "Colombo");
        when(driverProfileRepository.findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "Colombo"))
                .thenReturn(List.of(driver));
        when(vehicleRepository.findAllByDriverId("driver-1")).thenReturn(List.of());

        List<EligibleDriverResponse> responses = service.findEligibleDrivers("Colombo");

        assertThat(responses).isEmpty();
    }

    @Test
    void eligibleResponseContainsFirstReturnedVehicleInformation() {
        DriverProfile driver = eligibleDriver("driver-1", "account-1", "Colombo");
        Vehicle firstVehicle = vehicle("vehicle-1", "driver-1", "CAB-1234");
        Vehicle secondVehicle = vehicle("vehicle-2", "driver-1", "CAB-5678");
        when(driverProfileRepository.findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "Colombo"))
                .thenReturn(List.of(driver));
        when(vehicleRepository.findAllByDriverId("driver-1"))
                .thenReturn(List.of(firstVehicle, secondVehicle));

        EligibleDriverResponse response = service.findEligibleDrivers("Colombo").getFirst();

        assertThat(response.vehicleId()).isEqualTo("vehicle-1");
        assertThat(response.registrationNumber()).isEqualTo("CAB-1234");
        assertThat(response.vehicleType()).isEqualTo(VehicleType.CAR);
    }

    @Test
    void multipleEligibleDriversAreReturned() {
        DriverProfile firstDriver = eligibleDriver("driver-1", "account-1", "Colombo");
        DriverProfile secondDriver = eligibleDriver("driver-2", "account-2", "Colombo");
        when(driverProfileRepository.findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "Colombo"))
                .thenReturn(List.of(firstDriver, secondDriver));
        when(vehicleRepository.findAllByDriverId("driver-1"))
                .thenReturn(List.of(vehicle("vehicle-1", "driver-1", "CAB-1234")));
        when(vehicleRepository.findAllByDriverId("driver-2"))
                .thenReturn(List.of(vehicle("vehicle-2", "driver-2", "CAB-5678")));

        List<EligibleDriverResponse> responses = service.findEligibleDrivers("Colombo");

        assertThat(responses).extracting(EligibleDriverResponse::driverId)
                .containsExactly("driver-1", "driver-2");
    }

    @Test
    void noEligibleDriversReturnsEmptyList() {
        when(driverProfileRepository.findByAvailabilityStatusAndServiceAreaIgnoreCase(
                DriverAvailabilityStatus.AVAILABLE, "Colombo"))
                .thenReturn(List.of());

        List<EligibleDriverResponse> responses = service.findEligibleDrivers("Colombo");

        assertThat(responses).isEmpty();
    }

    @Test
    void blankServiceAreaIsRejectedByService() {
        assertThatThrownBy(() -> service.findEligibleDrivers("   "))
                .isInstanceOf(InvalidServiceAreaException.class)
                .hasMessageContaining("serviceArea");
        verifyNoInteractions(driverProfileRepository, vehicleRepository);
    }

    private DriverProfile eligibleDriver(String driverId, String accountId, String serviceArea) {
        LocalDateTime now = LocalDateTime.now();
        DriverProfile profile = new DriverProfile(
                driverId,
                accountId,
                "LIC-123",
                serviceArea,
                now.minusDays(1),
                now);
        profile.setAvailabilityStatus(DriverAvailabilityStatus.AVAILABLE);
        profile.setLatitude(6.9271);
        profile.setLongitude(79.8612);
        profile.setLocationUpdatedAt(now);
        return profile;
    }

    private Vehicle vehicle(String vehicleId, String driverId, String registrationNumber) {
        LocalDateTime now = LocalDateTime.now();
        return new Vehicle(
                vehicleId,
                driverId,
                registrationNumber,
                "Toyota",
                "Prius",
                "Blue",
                VehicleType.CAR,
                now,
                now);
    }
}
