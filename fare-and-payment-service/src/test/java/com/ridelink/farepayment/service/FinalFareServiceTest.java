package com.ridelink.farepayment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import com.ridelink.farepayment.config.FareProperties;
import com.ridelink.farepayment.dto.FinalFareRequest;
import com.ridelink.farepayment.dto.FinalFareResponse;
import com.ridelink.farepayment.exception.DuplicateFinalFareException;
import com.ridelink.farepayment.exception.FinalFareNotFoundException;
import com.ridelink.farepayment.exception.InvalidFinalFareException;
import com.ridelink.farepayment.model.FinalFare;
import com.ridelink.farepayment.repository.FinalFareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class FinalFareServiceTest {
    @Mock FinalFareRepository repository;
    private FinalFareService service;

    @BeforeEach
    void setUp() {
        service = new FinalFareService(repository, new FareProperties("LKR",
                money("150.00"), money("80.00"), money("250.00")));
        lenient().when(repository.save(any(FinalFare.class))).thenAnswer(call -> call.getArgument(0));
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }
    private FinalFareRequest request(String distanceKm) {
        return new FinalFareRequest("ride-123", money(distanceKm));
    }
    private FinalFare storedFare() {
        return new FinalFare("fare-123", "ride-123", money("12.50"), money("150.00"),
                money("80.00"), money("1000.00"), money("1150.00"), "LKR",
                LocalDateTime.of(2026, 10, 1, 12, 0));
    }
    private FinalFare saved() {
        ArgumentCaptor<FinalFare> captor = ArgumentCaptor.forClass(FinalFare.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test void validFinalFareIsCalculated() {
        assertEquals(money("1150.00"), service.calculate(request("12.50")).finalFare());
    }
    @Test void distanceFareIsCalculatedCorrectly() {
        assertEquals(money("1000.00"), service.calculate(request("12.50")).distanceFare());
    }
    @Test void baseFareIsAdded() {
        FinalFareService noMinimumService = new FinalFareService(repository,
                new FareProperties("LKR", money("150.00"), money("80.00"), money("0.00")));
        assertEquals(money("230.00"), noMinimumService.calculate(request("1.00")).finalFare());
    }
    @Test void minimumFareAppliesBelowThreshold() {
        assertEquals(money("250.00"), service.calculate(request("0.50")).finalFare());
    }
    @Test void calculatedFareAppliesAboveMinimum() {
        assertEquals(money("310.00"), service.calculate(request("2.00")).finalFare());
    }
    @Test void monetaryAmountsHaveTwoDecimalPlaces() {
        FinalFareResponse result = service.calculate(request("1.234"));
        assertEquals(2, result.distanceFare().scale());
        assertEquals(2, result.finalFare().scale());
        assertEquals(2, result.baseFare().scale());
        assertEquals(2, result.perKmRate().scale());
    }
    @Test void halfUpRoundingWorks() {
        assertEquals(money("98.72"), service.calculate(request("1.234")).distanceFare());
    }
    @Test void decimalArithmeticIsExact() {
        FinalFareService decimalService = new FinalFareService(repository,
                new FareProperties("LKR", money("0.00"), money("0.20"), money("0.00")));
        assertEquals(money("0.02"), decimalService.calculate(request("0.1")).finalFare());
    }
    @Test void idIsGeneratedAsUuid() {
        String id = service.calculate(request("12.50")).id();
        assertEquals(id, UUID.fromString(id).toString());
    }
    @Test void rideIdIsTrimmedBeforeLookupAndSave() {
        service.calculate(new FinalFareRequest("  ride-123  ", money("12.50")));
        verify(repository).existsByRideId("ride-123");
        assertEquals("ride-123", saved().getRideId());
    }
    @Test void configuredCurrencyIsUsed() {
        assertEquals("LKR", service.calculate(request("12.50")).currency());
    }
    @Test void configuredBaseFareIsUsed() {
        assertEquals(money("150.00"), service.calculate(request("12.50")).baseFare());
    }
    @Test void configuredPerKmRateIsUsed() {
        assertEquals(money("80.00"), service.calculate(request("12.50")).perKmRate());
    }
    @Test void createdAtIsSetAtCalculation() {
        LocalDateTime before = LocalDateTime.now();
        FinalFareResponse result = service.calculate(request("12.50"));
        LocalDateTime after = LocalDateTime.now();
        assertFalse(result.createdAt().isBefore(before));
        assertFalse(result.createdAt().isAfter(after));
    }
    @Test void repositorySaveIsCalledOnce() {
        service.calculate(request("12.50"));
        verify(repository, times(1)).save(any(FinalFare.class));
    }
    @Test void distanceAndRateSnapshotArePersisted() {
        service.calculate(request("12.50"));
        FinalFare fare = saved();
        assertEquals(money("12.50"), fare.getDistanceKm());
        assertEquals(money("150.00"), fare.getBaseFare());
        assertEquals(money("80.00"), fare.getPerKmRate());
        assertEquals("LKR", fare.getCurrency());
    }
    @Test void responseReflectsPersistedFare() {
        when(repository.save(any(FinalFare.class))).thenReturn(storedFare());
        assertEquals("fare-123", service.calculate(request("12.50")).id());
    }
    @Test void duplicateRideIsRejected() {
        when(repository.existsByRideId("ride-123")).thenReturn(true);
        assertThrows(DuplicateFinalFareException.class,
                () -> service.calculate(request("12.50")));
    }
    @Test void duplicateRideIsNeverSaved() {
        when(repository.existsByRideId("ride-123")).thenReturn(true);
        assertThrows(DuplicateFinalFareException.class,
                () -> service.calculate(request("12.50")));
        verify(repository, never()).save(any());
    }
    @Test void uniqueIndexRaceBecomesDuplicateConflict() {
        when(repository.save(any(FinalFare.class))).thenThrow(new DuplicateKeyException("index internals"));
        assertThrows(DuplicateFinalFareException.class,
                () -> service.calculate(request("12.50")));
    }
    @Test void getByIdSucceeds() {
        when(repository.findById("fare-123")).thenReturn(Optional.of(storedFare()));
        assertEquals(money("1150.00"), service.getById("fare-123").finalFare());
    }
    @Test void missingIdThrowsNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(FinalFareNotFoundException.class, () -> service.getById("missing"));
    }
    @Test void getByRideIdSucceeds() {
        when(repository.findByRideId("ride-123")).thenReturn(Optional.of(storedFare()));
        assertEquals("fare-123", service.getByRideId("ride-123").id());
    }
    @Test void getByRideIdTrimsInput() {
        when(repository.findByRideId("ride-123")).thenReturn(Optional.of(storedFare()));
        service.getByRideId("  ride-123  ");
        verify(repository).findByRideId("ride-123");
    }
    @Test void missingRideThrowsNotFound() {
        when(repository.findByRideId("missing")).thenReturn(Optional.empty());
        assertThrows(FinalFareNotFoundException.class, () -> service.getByRideId("missing"));
    }
    @Test void zeroDistanceIsRejectedBeforeRepositoryAccess() {
        assertThrows(InvalidFinalFareException.class, () -> service.calculate(request("0")));
        verifyNoInteractions(repository);
    }
    @Test void negativeDistanceIsRejected() {
        assertThrows(InvalidFinalFareException.class, () -> service.calculate(request("-1")));
    }
    @Test void excessiveDistanceIsRejected() {
        assertThrows(InvalidFinalFareException.class, () -> service.calculate(request("1000.01")));
    }
    @Test void maximumDistanceIsAccepted() {
        assertEquals(money("80150.00"), service.calculate(request("1000.0")).finalFare());
    }
    @Test void missingDistanceIsRejected() {
        assertThrows(InvalidFinalFareException.class,
                () -> service.calculate(new FinalFareRequest("ride-123", null)));
    }
    @Test void blankRideIdIsRejected() {
        assertThrows(InvalidFinalFareException.class,
                () -> service.calculate(new FinalFareRequest("   ", money("12.50"))));
    }
    @Test void overlyLongRideIdIsRejected() {
        assertThrows(InvalidFinalFareException.class,
                () -> service.calculate(new FinalFareRequest("r".repeat(101), money("12.50"))));
    }
    @Test void nullRequestIsRejected() {
        assertThrows(InvalidFinalFareException.class, () -> service.calculate(null));
    }
}
