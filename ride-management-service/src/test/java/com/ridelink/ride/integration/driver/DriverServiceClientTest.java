package com.ridelink.ride.integration.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ridelink.ride.exception.DriverServiceUnavailableException;
import com.ridelink.ride.exception.InvalidDriverServiceResponseException;
import com.ridelink.ride.integration.driver.dto.EligibleDriverResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class DriverServiceClientTest {

    private static final String TEST_INTERNAL_SERVICE_KEY = "test-only-internal-service-key";

    private MockRestServiceServer server;
    private DriverServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DriverServiceClient(
                builder,
                "http://driver-service.test",
                TEST_INTERNAL_SERVICE_KEY);
    }

    @Test
    void callsEligibleDriverEndpointAndDeserializesArray() {
        String body = """
                [
                  {
                    "driverId": "driver-1",
                    "accountId": "account-driver-1",
                    "serviceArea": "Colombo",
                    "latitude": 6.9271,
                    "longitude": 79.8612,
                    "availabilityStatus": "AVAILABLE",
                    "vehicleId": "vehicle-1",
                    "registrationNumber": "TEST-CAB-001",
                    "vehicleType": "CAR"
                  }
                ]
                """;
        server.expect(requestTo("http://driver-service.test/api/drivers/eligible?serviceArea=Colombo"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Key", TEST_INTERNAL_SERVICE_KEY))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<EligibleDriverResponse> response = client.getEligibleDrivers("Colombo");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().driverId()).isEqualTo("driver-1");
        assertThat(response.getFirst().accountId()).isEqualTo("account-driver-1");
        assertThat(response.getFirst().serviceArea()).isEqualTo("Colombo");
        assertThat(response.getFirst().latitude()).isEqualTo(6.9271);
        assertThat(response.getFirst().longitude()).isEqualTo(79.8612);
        assertThat(response.getFirst().availabilityStatus()).isEqualTo("AVAILABLE");
        assertThat(response.getFirst().vehicleId()).isEqualTo("vehicle-1");
        assertThat(response.getFirst().registrationNumber()).isEqualTo("TEST-CAB-001");
        assertThat(response.getFirst().vehicleType()).isEqualTo("CAR");
        server.verify();
    }

    @Test
    void encodesServiceAreaQueryParameter() {
        server.expect(requestTo("http://driver-service.test/api/drivers/eligible?serviceArea=Colombo%20North"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.getEligibleDrivers("Colombo North");

        server.verify();
    }

    @Test
    void returnsEmptyListForEmptyArray() {
        server.expect(requestTo("http://driver-service.test/api/drivers/eligible?serviceArea=Negombo"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<EligibleDriverResponse> response = client.getEligibleDrivers("Negombo");

        assertThat(response).isEmpty();
        server.verify();
    }

    @Test
    void mapsUpstreamServerErrorToUnavailableException() {
        server.expect(requestTo("http://driver-service.test/api/drivers/eligible?serviceArea=Colombo"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getEligibleDrivers("Colombo"))
                .isInstanceOf(DriverServiceUnavailableException.class)
                .hasMessage("Driver & Vehicle Service is currently unavailable");
        server.verify();
    }

    @Test
    void mapsOtherUpstreamHttpErrorToStableUnavailableException() {
        server.expect(requestTo("http://driver-service.test/api/drivers/eligible?serviceArea=Colombo"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.getEligibleDrivers("Colombo"))
                .isInstanceOf(DriverServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void mapsNetworkFailureToUnavailableException() {
        server.expect(requestTo("http://driver-service.test/api/drivers/eligible?serviceArea=Colombo"))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client.getEligibleDrivers("Colombo"))
                .isInstanceOf(DriverServiceUnavailableException.class)
                .hasMessageNotContaining("connection refused");
        server.verify();
    }

    @Test
    void mapsMalformedSuccessfulJsonToInvalidResponseException() {
        server.expect(requestTo("http://driver-service.test/api/drivers/eligible?serviceArea=Colombo"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getEligibleDrivers("Colombo"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);
        server.verify();
    }

    @Test
    void mapsMissingSuccessfulBodyToInvalidResponseException() {
        server.expect(requestTo("http://driver-service.test/api/drivers/eligible?serviceArea=Colombo"))
                .andRespond(withStatus(HttpStatus.OK));

        assertThatThrownBy(() -> client.getEligibleDrivers("Colombo"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);
        server.verify();
    }

    @Test
    void marksDriverUnavailableUsingPatchPathAndJsonBody() {
        server.expect(requestTo("http://driver-service.test/api/drivers/driver-1/availability"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Internal-Service-Key", TEST_INTERNAL_SERVICE_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"availabilityStatus":"UNAVAILABLE"}
                        """))
                .andRespond(withSuccess("""
                        {"id":"driver-1","availabilityStatus":"UNAVAILABLE","accountId":"account-1"}
                        """, MediaType.APPLICATION_JSON));

        client.markDriverUnavailable("driver-1");

        server.verify();
    }

    @Test
    void marksDriverAvailableUsingPatchPathAndJsonBody() {
        server.expect(requestTo("http://driver-service.test/api/drivers/driver%20two/availability"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Internal-Service-Key", TEST_INTERNAL_SERVICE_KEY))
                .andExpect(content().json("""
                        {"availabilityStatus":"AVAILABLE"}
                        """))
                .andRespond(withSuccess("""
                        {"id":"driver two","availabilityStatus":"AVAILABLE"}
                        """, MediaType.APPLICATION_JSON));

        client.markDriverAvailable("driver two");

        server.verify();
    }

    @Test
    void rejectsMissingAvailabilityResponseBody() {
        server.expect(requestTo("http://driver-service.test/api/drivers/driver-1/availability"))
                .andRespond(withStatus(HttpStatus.OK));

        assertThatThrownBy(() -> client.markDriverUnavailable("driver-1"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);
        server.verify();
    }

    @Test
    void rejectsMismatchedAvailabilityResponse() {
        server.expect(requestTo("http://driver-service.test/api/drivers/driver-1/availability"))
                .andRespond(withSuccess("""
                        {"id":"driver-1","availabilityStatus":"AVAILABLE"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.markDriverUnavailable("driver-1"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);
        server.verify();
    }

    @Test
    void rejectsMismatchedDriverIdInAvailabilityResponse() {
        server.expect(requestTo("http://driver-service.test/api/drivers/driver-1/availability"))
                .andRespond(withSuccess("""
                        {"id":"driver-2","availabilityStatus":"UNAVAILABLE"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.markDriverUnavailable("driver-1"))
                .isInstanceOf(InvalidDriverServiceResponseException.class);
        server.verify();
    }

    @Test
    void mapsAvailabilityUpstreamServerErrorToUnavailableException() {
        server.expect(requestTo("http://driver-service.test/api/drivers/driver-1/availability"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.markDriverAvailable("driver-1"))
                .isInstanceOf(DriverServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void mapsAvailabilityNetworkFailureToUnavailableException() {
        server.expect(requestTo("http://driver-service.test/api/drivers/driver-1/availability"))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client.markDriverAvailable("driver-1"))
                .isInstanceOf(DriverServiceUnavailableException.class)
                .hasMessageNotContaining("connection refused");
        server.verify();
    }

    @Test
    void doesNotLogInternalServiceKey(CapturedOutput output) {
        server.expect(requestTo("http://driver-service.test/api/drivers/eligible?serviceArea=Colombo"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.getEligibleDrivers("Colombo");

        assertThat(output).doesNotContain(TEST_INTERNAL_SERVICE_KEY);
        server.verify();
    }
}
