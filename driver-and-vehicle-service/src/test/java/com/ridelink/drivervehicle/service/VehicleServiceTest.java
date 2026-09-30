package com.ridelink.drivervehicle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ridelink.drivervehicle.dto.CreateVehicleRequest;
import com.ridelink.drivervehicle.dto.UpdateVehicleRequest;
import com.ridelink.drivervehicle.dto.VehicleResponse;
import com.ridelink.drivervehicle.exception.DriverProfileNotFoundException;
import com.ridelink.drivervehicle.exception.DuplicateVehicleRegistrationException;
import com.ridelink.drivervehicle.exception.VehicleNotFoundException;
import com.ridelink.drivervehicle.model.Vehicle;
import com.ridelink.drivervehicle.model.VehicleType;
import com.ridelink.drivervehicle.repository.DriverProfileRepository;
import com.ridelink.drivervehicle.repository.VehicleRepository;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    private VehicleService service;

    @BeforeEach
    void setUp() {
        service = new VehicleService(vehicleRepository, driverProfileRepository);
    }

    @Test
    void createReturnsSavedVehicleWithGeneratedIdAndTimestamps() {
        CreateVehicleRequest request = createRequest();
        when(driverProfileRepository.existsById("driver-1")).thenReturn(true);
        when(vehicleRepository.existsByRegistrationNumber("ABC-1234")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = service.create(request);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        Vehicle savedVehicle = captor.getValue();

        assertThat(response.id()).isNotBlank();
        assertThat(response.driverId()).isEqualTo("driver-1");
        assertThat(response.registrationNumber()).isEqualTo("ABC-1234");
        assertThat(response.vehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(savedVehicle.getCreatedAt()).isNotNull();
        assertThat(savedVehicle.getUpdatedAt()).isEqualTo(savedVehicle.getCreatedAt());
    }

    @Test
    void createRejectsMissingDriver() {
        when(driverProfileRepository.existsById("driver-1")).thenReturn(false);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(DriverProfileNotFoundException.class)
                .hasMessageContaining("driver-1");
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void createRejectsDuplicateRegistrationNumber() {
        when(driverProfileRepository.existsById("driver-1")).thenReturn(true);
        when(vehicleRepository.existsByRegistrationNumber("ABC-1234")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(DuplicateVehicleRegistrationException.class)
                .hasMessageContaining("ABC-1234");
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void getByIdReturnsVehicle() {
        when(vehicleRepository.findById("vehicle-1")).thenReturn(Optional.of(vehicle()));

        VehicleResponse response = service.getById("vehicle-1");

        assertThat(response.id()).isEqualTo("vehicle-1");
        assertThat(response.driverId()).isEqualTo("driver-1");
        assertThat(response.registrationNumber()).isEqualTo("ABC-1234");
    }

    @Test
    void getByDriverIdReturnsDriversVehicles() {
        when(driverProfileRepository.existsById("driver-1")).thenReturn(true);
        when(vehicleRepository.findAllByDriverId("driver-1")).thenReturn(List.of(vehicle()));

        List<VehicleResponse> responses = service.getByDriverId("driver-1");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().driverId()).isEqualTo("driver-1");
        verify(vehicleRepository).findAllByDriverId("driver-1");
    }

    @Test
    void updateChangesOnlyEditableFields() {
        Vehicle vehicle = vehicle();
        LocalDateTime originalCreatedAt = vehicle.getCreatedAt();
        when(vehicleRepository.findById("vehicle-1")).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByRegistrationNumber("XYZ-9876")).thenReturn(Optional.empty());
        when(vehicleRepository.save(vehicle)).thenReturn(vehicle);

        VehicleResponse response = service.update(
                "vehicle-1",
                new UpdateVehicleRequest(
                        "XYZ-9876", "Nissan", "Caravan", "White", VehicleType.VAN));

        assertThat(response.id()).isEqualTo("vehicle-1");
        assertThat(response.driverId()).isEqualTo("driver-1");
        assertThat(response.registrationNumber()).isEqualTo("XYZ-9876");
        assertThat(response.make()).isEqualTo("Nissan");
        assertThat(response.model()).isEqualTo("Caravan");
        assertThat(response.color()).isEqualTo("White");
        assertThat(response.vehicleType()).isEqualTo(VehicleType.VAN);
        assertThat(response.createdAt()).isEqualTo(originalCreatedAt);
        assertThat(response.updatedAt()).isAfter(originalCreatedAt);
    }

    @Test
    void getByIdThrowsWhenVehicleDoesNotExist() {
        when(vehicleRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOf(VehicleNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void deleteRemovesExistingVehicle() {
        Vehicle vehicle = vehicle();
        when(vehicleRepository.findById("vehicle-1")).thenReturn(Optional.of(vehicle));

        service.delete("vehicle-1");

        verify(vehicleRepository).delete(vehicle);
    }

    private CreateVehicleRequest createRequest() {
        return new CreateVehicleRequest(
                "driver-1",
                "ABC-1234",
                "Toyota",
                "Prius",
                "Blue",
                VehicleType.CAR);
    }

    private Vehicle vehicle() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        return new Vehicle(
                "vehicle-1",
                "driver-1",
                "ABC-1234",
                "Toyota",
                "Prius",
                "Blue",
                VehicleType.CAR,
                createdAt,
                createdAt);
    }
}
