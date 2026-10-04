package com.ridelink.ride.integration.farepayment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ridelink.ride.exception.FarePaymentServiceUnavailableException;
import com.ridelink.ride.exception.InvalidFarePaymentResponseException;
import com.ridelink.ride.exception.PaymentConflictException;
import com.ridelink.ride.integration.farepayment.dto.FinalFareResponse;
import com.ridelink.ride.integration.farepayment.dto.PaymentMethod;
import com.ridelink.ride.integration.farepayment.dto.PaymentResponse;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class FarePaymentServiceClientTest {
    private static final String TEST_INTERNAL_SERVICE_KEY = "test-only-internal-service-key";

    private MockRestServiceServer server;
    private FarePaymentServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FarePaymentServiceClient(
                builder,
                "http://fare-payment.test",
                TEST_INTERNAL_SERVICE_KEY);
    }

    @Test
    void postsFinalFareWithCorrectPathAndBody() {
        expectFinalFarePost(withSuccess(finalFareJson("ride-1", "12.50", "1150.00"),
                MediaType.APPLICATION_JSON));

        FinalFareResponse response = client.obtainFinalFare("ride-1", money("12.50"));

        assertThat(response.finalFare()).isEqualByComparingTo("1150.00");
        server.verify();
    }

    @Test
    void rejectsMissingFinalFareBody() {
        expectFinalFarePost(withStatus(HttpStatus.OK));
        assertThatThrownBy(() -> client.obtainFinalFare("ride-1", money("12.50")))
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void rejectsMismatchedFinalFareRideId() {
        expectFinalFarePost(withSuccess(finalFareJson("ride-2", "12.50", "1150.00"),
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.obtainFinalFare("ride-1", money("12.50")))
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void rejectsMismatchedFinalFareDistance() {
        expectFinalFarePost(withSuccess(finalFareJson("ride-1", "13.00", "1150.00"),
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.obtainFinalFare("ride-1", money("12.50")))
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void rejectsFinalFareResponseWithoutFinalFare() {
        expectFinalFarePost(withSuccess("""
                {"id":"fare-1","rideId":"ride-1","distanceKm":12.50,"currency":"LKR"}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.obtainFinalFare("ride-1", money("12.50")))
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00"})
    void rejectsNonPositiveFinalFare(String finalFare) {
        expectFinalFarePost(withSuccess(finalFareJson("ride-1", "12.50", finalFare),
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.obtainFinalFare("ride-1", money("12.50")))
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void finalFareConflictRetrievesAndReusesExistingRecord() {
        expectFinalFarePost(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo("http://fare-payment.test/api/fares/final/ride/ride-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Key", TEST_INTERNAL_SERVICE_KEY))
                .andExpect(request -> assertThat(request.getHeaders())
                        .doesNotContainKey("Authorization"))
                .andRespond(withSuccess(finalFareJson("ride-1", "12.50", "1150.00"),
                        MediaType.APPLICATION_JSON));

        assertThat(client.obtainFinalFare("ride-1", money("12.50")).id())
                .isEqualTo("fare-1");
        server.verify();
    }

    @Test
    void malformedExistingFinalFareIsRejected() {
        expectFinalFarePost(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo("http://fare-payment.test/api/fares/final/ride/ride-1"))
                .andExpect(header("X-Internal-Service-Key", TEST_INTERNAL_SERVICE_KEY))
                .andExpect(request -> assertThat(request.getHeaders())
                        .doesNotContainKey("Authorization"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.obtainFinalFare("ride-1", money("12.50")))
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void finalFareServerErrorMapsToUnavailable() {
        expectFinalFarePost(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.obtainFinalFare("ride-1", money("12.50")))
                .isInstanceOf(FarePaymentServiceUnavailableException.class);
    }

    @Test
    void finalFareNetworkErrorMapsToUnavailableWithoutLeakingDetails() {
        expectFinalFarePost(withException(new IOException("connection refused detail")));
        assertThatThrownBy(() -> client.obtainFinalFare("ride-1", money("12.50")))
                .isInstanceOf(FarePaymentServiceUnavailableException.class)
                .hasMessageNotContaining("connection refused detail");
    }

    @Test
    void unexpectedFinalFareClientErrorMapsToInvalidResponse() {
        expectFinalFarePost(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> client.obtainFinalFare("ride-1", money("12.50")))
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void postsCardPaymentWithTrustedFields() {
        expectPaymentPost(PaymentMethod.CARD, withSuccess(paymentJson(
                "ride-1", "passenger-1", "1150.00", "CARD", "PENDING"),
                MediaType.APPLICATION_JSON));

        PaymentResponse response = client.obtainPendingPayment(
                "ride-1", "passenger-1", money("1150.00"), PaymentMethod.CARD);

        assertThat(response.id()).isEqualTo("payment-1");
        server.verify();
    }

    @Test
    void postsCashPaymentMethod() {
        expectPaymentPost(PaymentMethod.CASH, withSuccess(paymentJson(
                "ride-1", "passenger-1", "1150.00", "CASH", "PENDING"),
                MediaType.APPLICATION_JSON));
        assertThat(client.obtainPendingPayment(
                "ride-1", "passenger-1", money("1150.00"), PaymentMethod.CASH).method())
                .isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void rejectsMissingPaymentBody() {
        expectPaymentPost(PaymentMethod.CARD, withStatus(HttpStatus.OK));
        assertThatThrownBy(() -> obtainCardPayment())
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void rejectsMismatchedNewPaymentRideId() {
        expectPaymentPost(PaymentMethod.CARD, withSuccess(paymentJson(
                "ride-2", "passenger-1", "1150.00", "CARD", "PENDING"),
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> obtainCardPayment())
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void rejectsMismatchedNewPaymentPassengerId() {
        expectPaymentPost(PaymentMethod.CARD, withSuccess(paymentJson(
                "ride-1", "passenger-2", "1150.00", "CARD", "PENDING"),
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> obtainCardPayment())
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void rejectsMismatchedNewPaymentAmount() {
        expectPaymentPost(PaymentMethod.CARD, withSuccess(paymentJson(
                "ride-1", "passenger-1", "1200.00", "CARD", "PENDING"),
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> obtainCardPayment())
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void rejectsNonPendingNewPayment() {
        expectPaymentPost(PaymentMethod.CARD, withSuccess(paymentJson(
                "ride-1", "passenger-1", "1150.00", "CARD", "COMPLETED"),
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> obtainCardPayment())
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void paymentConflictRetrievesAndReusesCompatiblePendingPayment() {
        expectPaymentPost(PaymentMethod.CARD, withStatus(HttpStatus.CONFLICT));
        expectPaymentGet(paymentJson("ride-1", "passenger-1", "1150.0", "CARD", "PENDING"));

        assertThat(obtainCardPayment().id()).isEqualTo("payment-1");
        server.verify();
    }

    @Test
    void existingPaymentWithDifferentMethodIsConflict() {
        expectPaymentPost(PaymentMethod.CARD, withStatus(HttpStatus.CONFLICT));
        expectPaymentGet(paymentJson("ride-1", "passenger-1", "1150.00", "CASH", "PENDING"));
        assertThatThrownBy(() -> obtainCardPayment()).isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void existingPaymentWithDifferentAmountIsConflict() {
        expectPaymentPost(PaymentMethod.CARD, withStatus(HttpStatus.CONFLICT));
        expectPaymentGet(paymentJson("ride-1", "passenger-1", "999.00", "CARD", "PENDING"));
        assertThatThrownBy(() -> obtainCardPayment()).isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void existingPaymentForDifferentPassengerIsConflict() {
        expectPaymentPost(PaymentMethod.CARD, withStatus(HttpStatus.CONFLICT));
        expectPaymentGet(paymentJson("ride-1", "passenger-2", "1150.00", "CARD", "PENDING"));
        assertThatThrownBy(() -> obtainCardPayment()).isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void existingNonPendingPaymentIsConflict() {
        expectPaymentPost(PaymentMethod.CARD, withStatus(HttpStatus.CONFLICT));
        expectPaymentGet(paymentJson("ride-1", "passenger-1", "1150.00", "CARD", "FAILED"));
        assertThatThrownBy(() -> obtainCardPayment()).isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void malformedExistingPaymentIsBadGatewayFailure() {
        expectPaymentPost(PaymentMethod.CARD, withStatus(HttpStatus.CONFLICT));
        expectPaymentGet("{}");
        assertThatThrownBy(() -> obtainCardPayment())
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void paymentServerErrorMapsToUnavailable() {
        expectPaymentPost(PaymentMethod.CARD, withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> obtainCardPayment())
                .isInstanceOf(FarePaymentServiceUnavailableException.class);
    }

    @Test
    void paymentNetworkErrorMapsToUnavailable() {
        expectPaymentPost(PaymentMethod.CARD, withException(new IOException("timeout detail")));
        assertThatThrownBy(() -> obtainCardPayment())
                .isInstanceOf(FarePaymentServiceUnavailableException.class)
                .hasMessageNotContaining("timeout detail");
    }

    @Test
    void missingPaymentAfterConflictMapsToInvalidResponse() {
        expectPaymentPost(PaymentMethod.CARD, withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo("http://fare-payment.test/api/payments/ride/ride-1"))
                .andExpect(header("X-Internal-Service-Key", TEST_INTERNAL_SERVICE_KEY))
                .andExpect(request -> assertThat(request.getHeaders())
                        .doesNotContainKey("Authorization"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> obtainCardPayment())
                .isInstanceOf(InvalidFarePaymentResponseException.class);
    }

    @Test
    void doesNotLogInternalServiceKey(CapturedOutput output) {
        expectFinalFarePost(withSuccess(
                finalFareJson("ride-1", "12.50", "1150.00"),
                MediaType.APPLICATION_JSON));

        client.obtainFinalFare("ride-1", money("12.50"));

        assertThat(output).doesNotContain(TEST_INTERNAL_SERVICE_KEY);
        server.verify();
    }

    private PaymentResponse obtainCardPayment() {
        return client.obtainPendingPayment(
                "ride-1", "passenger-1", money("1150.00"), PaymentMethod.CARD);
    }

    private void expectFinalFarePost(org.springframework.test.web.client.ResponseCreator response) {
        server.expect(requestTo("http://fare-payment.test/api/fares/final"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Key", TEST_INTERNAL_SERVICE_KEY))
                .andExpect(request -> assertThat(request.getHeaders())
                        .doesNotContainKey("Authorization"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"rideId":"ride-1","distanceKm":12.50}
                        """))
                .andRespond(response);
    }

    private void expectPaymentPost(
            PaymentMethod method,
            org.springframework.test.web.client.ResponseCreator response
    ) {
        server.expect(requestTo("http://fare-payment.test/api/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Key", TEST_INTERNAL_SERVICE_KEY))
                .andExpect(request -> assertThat(request.getHeaders())
                        .doesNotContainKey("Authorization"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"rideId":"ride-1","passengerId":"passenger-1","amount":1150.00,"method":"%s"}
                        """.formatted(method)))
                .andRespond(response);
    }

    private void expectPaymentGet(String body) {
        server.expect(requestTo("http://fare-payment.test/api/payments/ride/ride-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Key", TEST_INTERNAL_SERVICE_KEY))
                .andExpect(request -> assertThat(request.getHeaders())
                        .doesNotContainKey("Authorization"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String finalFareJson(String rideId, String distanceKm, String finalFare) {
        return """
                {"id":"fare-1","rideId":"%s","distanceKm":%s,"finalFare":%s,"currency":"LKR"}
                """.formatted(rideId, distanceKm, finalFare);
    }

    private String paymentJson(
            String rideId,
            String passengerId,
            String amount,
            String method,
            String status
    ) {
        return """
                {"id":"payment-1","rideId":"%s","passengerId":"%s","amount":%s,
                 "currency":"LKR","method":"%s","status":"%s"}
                """.formatted(rideId, passengerId, amount, method, status);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
