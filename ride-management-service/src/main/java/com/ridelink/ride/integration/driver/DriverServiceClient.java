package com.ridelink.ride.integration.driver;

import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.exception.InvalidDriverServiceResponseException;
import com.ridelink.ride.integration.driver.dto.DriverAvailabilityResponse;
import com.ridelink.ride.integration.driver.dto.DriverAvailabilityStatus;
import com.ridelink.ride.integration.driver.dto.EligibleDriverResponse;
import com.ridelink.ride.integration.driver.dto.UpdateDriverAvailabilityRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DriverServiceClient {

    private static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service-Key";

    private final RestClient restClient;
    private final String internalServiceKey;

    public DriverServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.driver.base-url}") String driverServiceBaseUrl,
            @Value("${security.internal.service-key}") String internalServiceKey
    ) {
        this.restClient = restClientBuilder.baseUrl(driverServiceBaseUrl).build();
        this.internalServiceKey = internalServiceKey;
    }

    public List<EligibleDriverResponse> getEligibleDrivers(String serviceArea) {
        try {
            EligibleDriverResponse[] response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/drivers/eligible")
                            .queryParam("serviceArea", serviceArea)
                            .build())
                    .header(INTERNAL_SERVICE_HEADER, internalServiceKey)
                    .retrieve()
                    .body(EligibleDriverResponse[].class);

            if (response == null) {
                throw new InvalidDriverServiceResponseException();
            }
            return Arrays.asList(response);
        } catch (InvalidDriverServiceResponseException exception) {
            throw exception;
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new DriverServiceUnavailableException();
        } catch (RestClientException exception) {
            throw new InvalidDriverServiceResponseException();
        }
    }

    public void markDriverUnavailable(String driverId) {
        updateAvailability(driverId, DriverAvailabilityStatus.UNAVAILABLE);
    }

    public void markDriverAvailable(String driverId) {
        updateAvailability(driverId, DriverAvailabilityStatus.AVAILABLE);
    }

    private void updateAvailability(String driverId, DriverAvailabilityStatus availabilityStatus) {
        try {
            DriverAvailabilityResponse response = restClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/drivers/{driverId}/availability")
                            .build(driverId))
                    .header(INTERNAL_SERVICE_HEADER, internalServiceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UpdateDriverAvailabilityRequest(availabilityStatus))
                    .retrieve()
                    .body(DriverAvailabilityResponse.class);

            if (response == null
                    || response.id() == null
                    || !response.id().equals(driverId)
                    || response.availabilityStatus() != availabilityStatus) {
                throw new InvalidDriverServiceResponseException();
            }
        } catch (InvalidDriverServiceResponseException exception) {
            throw exception;
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new DriverServiceUnavailableException();
        } catch (RestClientException exception) {
            throw new InvalidDriverServiceResponseException();
        }
    }
}
