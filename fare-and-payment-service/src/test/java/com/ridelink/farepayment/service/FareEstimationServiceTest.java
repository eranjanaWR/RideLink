package com.ridelink.farepayment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.ridelink.farepayment.config.FareProperties;
import com.ridelink.farepayment.dto.FareEstimateRequest;
import com.ridelink.farepayment.dto.FareEstimateResponse;
import com.ridelink.farepayment.exception.FareEstimateNotFoundException;
import com.ridelink.farepayment.exception.InvalidFareEstimateException;
import com.ridelink.farepayment.model.FareEstimate;
import com.ridelink.farepayment.repository.FareEstimateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FareEstimationServiceTest {
    @Mock FareEstimateRepository repository;
    private FareEstimationService service;

    @BeforeEach
    void setUp() {
        service = new FareEstimationService(repository, new FareProperties("LKR",
                money("150.00"), money("80.00"), money("250.00")));
        lenient().when(repository.save(any(FareEstimate.class))).thenAnswer(call -> call.getArgument(0));
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }

    private FareEstimateRequest request(String distance) {
        return new FareEstimateRequest("Colombo Fort", "Bambalapitiya", money(distance));
    }

    private FareEstimate savedEstimate() {
        return new FareEstimate("saved-id", "Colombo Fort", "Bambalapitiya", money("7.50"),
                money("150.00"), money("80.00"), money("600.00"), money("750.00"), "LKR",
                LocalDateTime.of(2026, 10, 1, 12, 0));
    }

    private FareEstimate captureSavedEstimate() {
        ArgumentCaptor<FareEstimate> captor = ArgumentCaptor.forClass(FareEstimate.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test void distanceFareIsDistanceTimesRate() {
        assertEquals(money("600.00"), service.estimate(request("7.50")).distanceFare());
    }

    @Test void baseFareIsAdded() {
        assertEquals(money("750.00"), service.estimate(request("7.50")).estimatedFare());
    }

    @Test void minimumFareAppliesBelowThreshold() {
        assertEquals(money("250.00"), service.estimate(request("0.50")).estimatedFare());
    }

    @Test void calculatedFareAppliesAboveMinimum() {
        assertEquals(money("310.00"), service.estimate(request("2.00")).estimatedFare());
    }

    @Test void finalMoneyHasTwoDecimalPlaces() {
        FareEstimateResponse result = service.estimate(request("1.234"));
        assertEquals(2, result.distanceFare().scale());
        assertEquals(2, result.estimatedFare().scale());
    }

    @Test void fractionalDistanceRoundsHalfUp() {
        assertEquals(money("98.72"), service.estimate(request("1.234")).distanceFare());
    }

    @Test void decimalArithmeticAvoidsBinaryFloatingPoint() {
        FareEstimationService decimalService = new FareEstimationService(repository,
                new FareProperties("LKR", money("0.00"), money("0.20"), money("0.00")));
        FareEstimateResponse result = decimalService.estimate(request("0.1"));
        assertEquals(money("0.02"), result.distanceFare());
        assertEquals(money("0.02"), result.estimatedFare());
    }

    @Test void generatedIdIsUuid() {
        String id = service.estimate(request("7.50")).id();
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test void createdAtIsSetAtEstimation() {
        LocalDateTime before = LocalDateTime.now();
        FareEstimateResponse result = service.estimate(request("7.50"));
        LocalDateTime after = LocalDateTime.now();
        assertFalse(result.createdAt().isBefore(before));
        assertFalse(result.createdAt().isAfter(after));
    }

    @Test void configuredCurrencyIsUsed() {
        assertEquals("LKR", service.estimate(request("7.50")).currency());
    }

    @Test void configuredBaseFareIsUsed() {
        assertEquals(money("150.00"), service.estimate(request("7.50")).baseFare());
    }

    @Test void configuredPerKmRateIsUsed() {
        assertEquals(money("80.00"), service.estimate(request("7.50")).perKmRate());
    }

    @Test void pickupIsTrimmed() {
        FareEstimateResponse result = service.estimate(new FareEstimateRequest("  Colombo Fort  ",
                "Bambalapitiya", money("7.50")));
        assertEquals("Colombo Fort", result.pickupLocation());
    }

    @Test void destinationIsTrimmed() {
        FareEstimateResponse result = service.estimate(new FareEstimateRequest("Colombo Fort",
                "  Bambalapitiya  ", money("7.50")));
        assertEquals("Bambalapitiya", result.destinationLocation());
    }

    @Test void distanceIsPersisted() {
        service.estimate(request("7.50"));
        assertEquals(money("7.50"), captureSavedEstimate().getDistanceKm());
    }

    @Test void quotePersistsItsRatesAndCurrency() {
        service.estimate(request("7.50"));
        FareEstimate saved = captureSavedEstimate();
        assertEquals(money("150.00"), saved.getBaseFare());
        assertEquals(money("80.00"), saved.getPerKmRate());
        assertEquals("LKR", saved.getCurrency());
    }

    @Test void repositorySaveIsCalledOnce() {
        service.estimate(request("7.50"));
        verify(repository, times(1)).save(any(FareEstimate.class));
    }

    @Test void responseReflectsSavedDocument() {
        when(repository.save(any(FareEstimate.class))).thenReturn(savedEstimate());
        FareEstimateResponse result = service.estimate(request("7.50"));
        assertEquals("saved-id", result.id());
        assertEquals(money("750.00"), result.estimatedFare());
    }

    @Test void equalLocationsAreRejectedIgnoringCase() {
        assertThrows(InvalidFareEstimateException.class, () -> service.estimate(
                new FareEstimateRequest("Colombo Fort", "COLOMBO FORT", money("2.00"))));
    }

    @Test void equalLocationsAreRejectedAfterTrimming() {
        assertThrows(InvalidFareEstimateException.class, () -> service.estimate(
                new FareEstimateRequest("  Colombo Fort", "Colombo Fort  ", money("2.00"))));
    }

    @Test void invalidSameLocationIsNeverSaved() {
        assertThrows(InvalidFareEstimateException.class, () -> service.estimate(
                new FareEstimateRequest("  Fort  ", "FORT", money("2.00"))));
        verifyNoInteractions(repository);
    }

    @Test void existingEstimateCanBeRetrieved() {
        when(repository.findById("saved-id")).thenReturn(Optional.of(savedEstimate()));
        FareEstimateResponse result = service.getEstimate("saved-id");
        assertEquals("saved-id", result.id());
        assertEquals(money("750.00"), result.estimatedFare());
    }

    @Test void missingEstimateThrowsNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(FareEstimateNotFoundException.class, () -> service.getEstimate("missing"));
    }

    @Test void missingDistanceIsRejected() {
        assertThrows(InvalidFareEstimateException.class, () -> service.estimate(
                new FareEstimateRequest("Fort", "Bambalapitiya", null)));
        verifyNoInteractions(repository);
    }

    @Test void zeroDistanceIsRejected() {
        assertThrows(InvalidFareEstimateException.class, () -> service.estimate(request("0")));
        verifyNoInteractions(repository);
    }

    @Test void negativeDistanceIsRejected() {
        assertThrows(InvalidFareEstimateException.class, () -> service.estimate(request("-1")));
        verifyNoInteractions(repository);
    }

    @Test void excessiveDistanceIsRejected() {
        assertThrows(InvalidFareEstimateException.class, () -> service.estimate(request("1000.01")));
        verifyNoInteractions(repository);
    }

    @Test void maximumDistanceIsAccepted() {
        assertEquals(money("80150.00"), service.estimate(request("1000.0")).estimatedFare());
    }

    @Test void missingPickupIsRejected() {
        assertThrows(InvalidFareEstimateException.class, () -> service.estimate(
                new FareEstimateRequest(null, "Bambalapitiya", money("2.00"))));
    }

    @Test void missingDestinationIsRejected() {
        assertThrows(InvalidFareEstimateException.class, () -> service.estimate(
                new FareEstimateRequest("Colombo Fort", null, money("2.00"))));
    }

    @Test void overlyLongLocationIsRejected() {
        assertThrows(InvalidFareEstimateException.class, () -> service.estimate(
                new FareEstimateRequest("A".repeat(151), "Bambalapitiya", money("2.00"))));
    }
}
