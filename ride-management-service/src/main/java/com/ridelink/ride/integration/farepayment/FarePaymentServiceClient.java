package com.ridelink.ride.integration.farepayment;

import com.ridelink.ride.exception.FarePaymentServiceUnavailableException;
import com.ridelink.ride.exception.InvalidFarePaymentResponseException;
import com.ridelink.ride.exception.PaymentConflictException;
import com.ridelink.ride.integration.farepayment.dto.CreatePaymentRequest;
import com.ridelink.ride.integration.farepayment.dto.FinalFareRequest;
import com.ridelink.ride.integration.farepayment.dto.FinalFareResponse;
import com.ridelink.ride.integration.farepayment.dto.PaymentMethod;
import com.ridelink.ride.integration.farepayment.dto.PaymentResponse;
import com.ridelink.ride.integration.farepayment.dto.PaymentStatus;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FarePaymentServiceClient {
    private final RestClient restClient;

    public FarePaymentServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.fare-payment.base-url}") String farePaymentServiceBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(farePaymentServiceBaseUrl).build();
    }

    public FinalFareResponse obtainFinalFare(String rideId, BigDecimal distanceKm) {
        try {
            FinalFareResponse response = restClient.post()
                    .uri("/api/fares/final")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new FinalFareRequest(rideId, distanceKm))
                    .retrieve()
                    .body(FinalFareResponse.class);
            return validateFinalFare(response, rideId, distanceKm);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                return getExistingFinalFare(rideId, distanceKm);
            }
            throw mapResponseFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new FarePaymentServiceUnavailableException();
        } catch (RestClientException exception) {
            throw new InvalidFarePaymentResponseException();
        }
    }

    public PaymentResponse obtainPendingPayment(
            String rideId,
            String passengerId,
            BigDecimal amount,
            PaymentMethod method
    ) {
        try {
            PaymentResponse response = restClient.post()
                    .uri("/api/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreatePaymentRequest(rideId, passengerId, amount, method))
                    .retrieve()
                    .body(PaymentResponse.class);
            return validateNewPayment(response, rideId, passengerId, amount, method);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                return getExistingPayment(rideId, passengerId, amount, method);
            }
            throw mapResponseFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new FarePaymentServiceUnavailableException();
        } catch (RestClientException exception) {
            throw new InvalidFarePaymentResponseException();
        }
    }

    private FinalFareResponse getExistingFinalFare(String rideId, BigDecimal distanceKm) {
        try {
            FinalFareResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/fares/final/ride/{rideId}")
                            .build(rideId))
                    .retrieve()
                    .body(FinalFareResponse.class);
            return validateFinalFare(response, rideId, distanceKm);
        } catch (RestClientResponseException exception) {
            throw mapResponseFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new FarePaymentServiceUnavailableException();
        } catch (RestClientException exception) {
            throw new InvalidFarePaymentResponseException();
        }
    }

    private PaymentResponse getExistingPayment(
            String rideId,
            String passengerId,
            BigDecimal amount,
            PaymentMethod method
    ) {
        try {
            PaymentResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/payments/ride/{rideId}")
                            .build(rideId))
                    .retrieve()
                    .body(PaymentResponse.class);
            validatePaymentStructure(response);
            if (!response.rideId().equals(rideId)
                    || !response.passengerId().equals(passengerId)
                    || response.amount().compareTo(amount) != 0
                    || response.method() != method
                    || response.status() != PaymentStatus.PENDING) {
                throw new PaymentConflictException();
            }
            return response;
        } catch (PaymentConflictException | InvalidFarePaymentResponseException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapResponseFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new FarePaymentServiceUnavailableException();
        } catch (RestClientException exception) {
            throw new InvalidFarePaymentResponseException();
        }
    }

    private FinalFareResponse validateFinalFare(
            FinalFareResponse response,
            String rideId,
            BigDecimal distanceKm
    ) {
        if (response == null
                || response.id() == null
                || response.id().isBlank()
                || !rideId.equals(response.rideId())
                || response.distanceKm() == null
                || response.distanceKm().compareTo(distanceKm) != 0
                || response.finalFare() == null
                || response.finalFare().signum() <= 0
                || response.currency() == null
                || response.currency().isBlank()) {
            throw new InvalidFarePaymentResponseException();
        }
        return response;
    }

    private PaymentResponse validateNewPayment(
            PaymentResponse response,
            String rideId,
            String passengerId,
            BigDecimal amount,
            PaymentMethod method
    ) {
        validatePaymentStructure(response);
        if (!response.rideId().equals(rideId)
                || !response.passengerId().equals(passengerId)
                || response.amount().compareTo(amount) != 0
                || response.method() != method
                || response.status() != PaymentStatus.PENDING) {
            throw new InvalidFarePaymentResponseException();
        }
        return response;
    }

    private void validatePaymentStructure(PaymentResponse response) {
        if (response == null
                || response.id() == null
                || response.id().isBlank()
                || response.rideId() == null
                || response.passengerId() == null
                || response.amount() == null
                || response.amount().signum() <= 0
                || response.currency() == null
                || response.currency().isBlank()
                || response.method() == null
                || response.status() == null) {
            throw new InvalidFarePaymentResponseException();
        }
    }

    private RuntimeException mapResponseFailure(RestClientResponseException exception) {
        if (exception.getStatusCode().is5xxServerError()) {
            return new FarePaymentServiceUnavailableException();
        }
        return new InvalidFarePaymentResponseException();
    }
}
