package com.ridelink.drivervehicle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ridelink.drivervehicle.dto.CreateDriverProfileRequest;
import com.ridelink.drivervehicle.dto.DriverProfileResponse;
import com.ridelink.drivervehicle.dto.UpdateDriverAvailabilityRequest;
import com.ridelink.drivervehicle.dto.UpdateDriverLocationRequest;
import com.ridelink.drivervehicle.dto.UpdateDriverProfileRequest;
import com.ridelink.drivervehicle.exception.DriverProfileNotFoundException;
import com.ridelink.drivervehicle.exception.DuplicateDriverProfileException;
import com.ridelink.drivervehicle.model.DriverProfile;
import com.ridelink.drivervehicle.model.DriverAvailabilityStatus;
import com.ridelink.drivervehicle.repository.DriverProfileRepository;

@ExtendWith(MockitoExtension.class)
class DriverProfileServiceTest {

    @Mock
    private DriverProfileRepository repository;

    private DriverProfileService service;

    @BeforeEach
    void setUp() {
        service = new DriverProfileService(repository);
    }

    @Test
    void createReturnsSavedProfileWithGeneratedIdAndTimestamps() {
        CreateDriverProfileRequest request = new CreateDriverProfileRequest(
                "account-1", "LIC-123", "Colombo");
        when(repository.existsByAccountId("account-1")).thenReturn(false);
        when(repository.save(any(DriverProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DriverProfileResponse response = service.create(request);

        ArgumentCaptor<DriverProfile> captor = ArgumentCaptor.forClass(DriverProfile.class);
        verify(repository).save(captor.capture());
        DriverProfile savedProfile = captor.getValue();

        assertThat(response.id()).isNotBlank();
        assertThat(response.accountId()).isEqualTo("account-1");
        assertThat(response.licenseNumber()).isEqualTo("LIC-123");
        assertThat(response.serviceArea()).isEqualTo("Colombo");
        assertThat(response.availabilityStatus()).isEqualTo(DriverAvailabilityStatus.UNAVAILABLE);
        assertThat(savedProfile.getCreatedAt()).isNotNull();
        assertThat(savedProfile.getUpdatedAt()).isEqualTo(savedProfile.getCreatedAt());
    }

    @Test
    void createRejectsDuplicateAccountId() {
        CreateDriverProfileRequest request = new CreateDriverProfileRequest(
                "account-1", "LIC-123", "Colombo");
        when(repository.existsByAccountId("account-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateDriverProfileException.class)
                .hasMessageContaining("account-1");
        verify(repository, never()).save(any());
    }

    @Test
    void getByIdReturnsProfile() {
        DriverProfile profile = profile();
        when(repository.findById("driver-1")).thenReturn(Optional.of(profile));

        DriverProfileResponse response = service.getById("driver-1");

        assertThat(response.id()).isEqualTo("driver-1");
        assertThat(response.accountId()).isEqualTo("account-1");
    }

    @Test
    void getByIdThrowsWhenDriverDoesNotExist() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOf(DriverProfileNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void updateChangesOnlyEditableFields() {
        DriverProfile profile = profile();
        profile.setAvailabilityStatus(DriverAvailabilityStatus.AVAILABLE);
        profile.setLatitude(6.9271);
        profile.setLongitude(79.8612);
        profile.setLocationUpdatedAt(LocalDateTime.now().minusHours(1));
        LocalDateTime originalUpdatedAt = profile.getUpdatedAt();
        LocalDateTime originalLocationUpdatedAt = profile.getLocationUpdatedAt();
        when(repository.findById("driver-1")).thenReturn(Optional.of(profile));
        when(repository.save(profile)).thenReturn(profile);

        DriverProfileResponse response = service.update(
                "driver-1",
                new UpdateDriverProfileRequest("LIC-999", "Kandy"));

        assertThat(response.id()).isEqualTo("driver-1");
        assertThat(response.accountId()).isEqualTo("account-1");
        assertThat(response.licenseNumber()).isEqualTo("LIC-999");
        assertThat(response.serviceArea()).isEqualTo("Kandy");
        assertThat(response.availabilityStatus()).isEqualTo(DriverAvailabilityStatus.AVAILABLE);
        assertThat(response.latitude()).isEqualTo(6.9271);
        assertThat(response.longitude()).isEqualTo(79.8612);
        assertThat(response.locationUpdatedAt()).isEqualTo(originalLocationUpdatedAt);
        assertThat(response.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        verify(repository).save(profile);
    }

    @Test
    void createDefaultsAvailabilityToUnavailable() {
        CreateDriverProfileRequest request = new CreateDriverProfileRequest(
                "account-1", "LIC-123", "Colombo");
        when(repository.existsByAccountId("account-1")).thenReturn(false);
        when(repository.save(any(DriverProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DriverProfileResponse response = service.create(request);

        assertThat(response.availabilityStatus()).isEqualTo(DriverAvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void updateAvailabilityChangesUnavailableToAvailable() {
        DriverProfile profile = profile();
        LocalDateTime originalUpdatedAt = profile.getUpdatedAt();
        when(repository.findById("driver-1")).thenReturn(Optional.of(profile));
        when(repository.save(profile)).thenReturn(profile);

        DriverProfileResponse response = service.updateAvailability(
                "driver-1",
                new UpdateDriverAvailabilityRequest(DriverAvailabilityStatus.AVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(DriverAvailabilityStatus.AVAILABLE);
        assertThat(response.accountId()).isEqualTo("account-1");
        assertThat(response.licenseNumber()).isEqualTo("LIC-123");
        assertThat(response.serviceArea()).isEqualTo("Colombo");
        assertThat(response.updatedAt()).isAfter(originalUpdatedAt);
        verify(repository).save(profile);
    }

    @Test
    void updateAvailabilityChangesAvailableToUnavailable() {
        DriverProfile profile = profile();
        profile.setAvailabilityStatus(DriverAvailabilityStatus.AVAILABLE);
        when(repository.findById("driver-1")).thenReturn(Optional.of(profile));
        when(repository.save(profile)).thenReturn(profile);

        DriverProfileResponse response = service.updateAvailability(
                "driver-1",
                new UpdateDriverAvailabilityRequest(DriverAvailabilityStatus.UNAVAILABLE));

        assertThat(response.availabilityStatus()).isEqualTo(DriverAvailabilityStatus.UNAVAILABLE);
        verify(repository).save(profile);
    }

    @Test
    void updateAvailabilityThrowsWhenDriverDoesNotExist() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAvailability(
                "missing",
                new UpdateDriverAvailabilityRequest(DriverAvailabilityStatus.AVAILABLE)))
                .isInstanceOf(DriverProfileNotFoundException.class)
                .hasMessageContaining("missing");
        verify(repository, never()).save(any());
    }

    @Test
    void getByIdTreatsLegacyNullAvailabilityAsUnavailable() {
        DriverProfile profile = profile();
        profile.setAvailabilityStatus(null);
        when(repository.findById("driver-1")).thenReturn(Optional.of(profile));

        DriverProfileResponse response = service.getById("driver-1");

        assertThat(response.availabilityStatus()).isEqualTo(DriverAvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void updateLocationUpdatesCoordinatesAndTimestampsWithoutChangingOtherFields() {
        DriverProfile profile = profile();
        profile.setAvailabilityStatus(DriverAvailabilityStatus.AVAILABLE);
        LocalDateTime originalUpdatedAt = profile.getUpdatedAt();
        when(repository.findById("driver-1")).thenReturn(Optional.of(profile));
        when(repository.save(profile)).thenReturn(profile);

        DriverProfileResponse response = service.updateLocation(
                "driver-1",
                new UpdateDriverLocationRequest(6.9271, 79.8612));

        assertThat(response.latitude()).isEqualTo(6.9271);
        assertThat(response.longitude()).isEqualTo(79.8612);
        assertThat(response.locationUpdatedAt()).isNotNull();
        assertThat(response.updatedAt()).isAfter(originalUpdatedAt);
        assertThat(response.updatedAt()).isEqualTo(response.locationUpdatedAt());
        assertThat(response.id()).isEqualTo("driver-1");
        assertThat(response.accountId()).isEqualTo("account-1");
        assertThat(response.licenseNumber()).isEqualTo("LIC-123");
        assertThat(response.serviceArea()).isEqualTo("Colombo");
        assertThat(response.availabilityStatus()).isEqualTo(DriverAvailabilityStatus.AVAILABLE);
        verify(repository).save(profile);
    }

    @Test
    void updateLocationThrowsWhenDriverDoesNotExist() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLocation(
                "missing",
                new UpdateDriverLocationRequest(6.9271, 79.8612)))
                .isInstanceOf(DriverProfileNotFoundException.class)
                .hasMessageContaining("missing");
        verify(repository, never()).save(any());
    }

    @Test
    void getByIdSafelyReturnsNullLegacyLocationFields() {
        DriverProfile profile = profile();
        when(repository.findById("driver-1")).thenReturn(Optional.of(profile));

        DriverProfileResponse response = service.getById("driver-1");

        assertThat(response.latitude()).isNull();
        assertThat(response.longitude()).isNull();
        assertThat(response.locationUpdatedAt()).isNull();
    }

    private DriverProfile profile() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        return new DriverProfile(
                "driver-1",
                "account-1",
                "LIC-123",
                "Colombo",
                createdAt,
                createdAt);
    }
}
