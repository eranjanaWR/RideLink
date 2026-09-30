package com.ridelink.ride.integration.driver;

import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.exception.InvalidDriverServiceResponseException;
import com.ridelink.ride.integration.driver.dto.EligibleDriverResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DriverServiceClient {

    private final RestClient restClient;

    public DriverServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.driver.base-url}") String driverServiceBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(driverServiceBaseUrl).build();
    }

    public List<EligibleDriverResponse> getEligibleDrivers(String serviceArea) {
        try {
            EligibleDriverResponse[] response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/drivers/eligible")
                            .queryParam("serviceArea", serviceArea)
                            .build())
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
}
