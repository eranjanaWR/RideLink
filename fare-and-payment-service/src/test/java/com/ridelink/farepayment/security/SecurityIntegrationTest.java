package com.ridelink.farepayment.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ridelink.farepayment.config.SecurityConfig;
import com.ridelink.farepayment.controller.FareController;
import com.ridelink.farepayment.controller.FinalFareController;
import com.ridelink.farepayment.controller.PaymentController;
import com.ridelink.farepayment.dto.CreatePaymentRequest;
import com.ridelink.farepayment.dto.FareEstimateRequest;
import com.ridelink.farepayment.dto.FareEstimateResponse;
import com.ridelink.farepayment.dto.FinalFareRequest;
import com.ridelink.farepayment.dto.FinalFareResponse;
import com.ridelink.farepayment.dto.PaymentResponse;
import com.ridelink.farepayment.exception.DuplicateFinalFareException;
import com.ridelink.farepayment.exception.DuplicatePaymentException;
import com.ridelink.farepayment.exception.GlobalExceptionHandler;
import com.ridelink.farepayment.exception.InvalidPaymentStateException;
import com.ridelink.farepayment.model.PaymentMethod;
import com.ridelink.farepayment.model.PaymentStatus;
import com.ridelink.farepayment.service.FareEstimationService;
import com.ridelink.farepayment.service.FinalFareService;
import com.ridelink.farepayment.service.PaymentService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest({
        FareController.class,
        FinalFareController.class,
        PaymentController.class,
        SwaggerProbeController.class
})
@Import({
        SecurityConfig.class,
        JwtTokenValidator.class,
        InternalServiceAuthenticator.class,
        SecurityErrorHandler.class,
        FarePaymentAuthorizationService.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "security.jwt.secret=test-only-jwt-secret-that-is-at-least-32-bytes-long",
        "security.internal.service-key=test-only-internal-service-key"
})
@ExtendWith(OutputCaptureExtension.class)
class SecurityIntegrationTest {

    private static final String JWT_SECRET =
            "test-only-jwt-secret-that-is-at-least-32-bytes-long";
    private static final String INTERNAL_KEY = "test-only-internal-service-key";
    private static final String OWNER = "passenger-1";
    private static final String OTHER_PASSENGER = "passenger-2";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FareEstimationService fareEstimationService;

    @MockitoBean
    private FinalFareService finalFareService;

    @MockitoBean
    private PaymentService paymentService;

    @BeforeEach
    void setUpResponses() {
        when(fareEstimationService.estimate(any(FareEstimateRequest.class)))
                .thenReturn(estimateResponse());
        when(fareEstimationService.getEstimate("estimate-1"))
                .thenReturn(estimateResponse());
        when(finalFareService.calculate(any(FinalFareRequest.class)))
                .thenReturn(finalFareResponse());
        when(finalFareService.getById("fare-1")).thenReturn(finalFareResponse());
        when(finalFareService.getByRideId("ride-1")).thenReturn(finalFareResponse());
        when(paymentService.createPayment(any(CreatePaymentRequest.class)))
                .thenReturn(paymentResponse(PaymentStatus.PENDING));
        when(paymentService.getPayment("payment-1"))
                .thenReturn(paymentResponse(PaymentStatus.PENDING));
        when(paymentService.getPaymentByRide("ride-1"))
                .thenReturn(paymentResponse(PaymentStatus.PENDING));
        when(paymentService.completePayment("payment-1"))
                .thenReturn(paymentResponse(PaymentStatus.COMPLETED));
        when(paymentService.failPayment("payment-1"))
                .thenReturn(paymentResponse(PaymentStatus.FAILED));
    }

    @Test
    void validPassengerJwtIsAccepted() throws Exception {
        postEstimate(passengerToken()).andExpect(status().isCreated());
    }

    @Test
    void validDriverJwtIsAccepted() throws Exception {
        postEstimate(driverToken()).andExpect(status().isCreated());
    }

    @Test
    void validAdminJwtIsAccepted() throws Exception {
        postEstimate(adminToken()).andExpect(status().isCreated());
    }

    @Test
    void missingAuthenticationReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(post("/api/fares/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(estimateJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {
        postEstimate("malformed").andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        String token = jwt(OWNER, AccountRole.PASSENGER, AccountStatus.ACTIVE,
                Instant.now().plusSeconds(60),
                "different-test-secret-that-is-also-at-least-32-bytes");

        postEstimate(token).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        String token = jwt(OWNER, AccountRole.PASSENGER, AccountStatus.ACTIVE,
                Instant.now().minusSeconds(60), JWT_SECRET);

        postEstimate(token).andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedJwtReturnsJsonForbidden() throws Exception {
        String token = jwt(OWNER, AccountRole.PASSENGER, AccountStatus.SUSPENDED,
                Instant.now().plusSeconds(60), JWT_SECRET);

        postEstimate(token)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void disabledJwtReturnsForbidden() throws Exception {
        String token = jwt(OWNER, AccountRole.PASSENGER, AccountStatus.DISABLED,
                Instant.now().plusSeconds(60), JWT_SECRET);

        postEstimate(token).andExpect(status().isForbidden());
    }

    @Test
    void passengerCreatesFareEstimate() throws Exception {
        postEstimate(passengerToken()).andExpect(status().isCreated());
    }

    @Test
    void driverCreatesFareEstimate() throws Exception {
        postEstimate(driverToken()).andExpect(status().isCreated());
    }

    @Test
    void adminCreatesFareEstimate() throws Exception {
        postEstimate(adminToken()).andExpect(status().isCreated());
    }

    @Test
    void authenticatedUserRetrievesFareEstimate() throws Exception {
        mockMvc.perform(get("/api/fares/estimates/estimate-1")
                        .header("Authorization", bearer(driverToken())))
                .andExpect(status().isOk());
    }

    @Test
    void fareEstimateResponsePreservesDeterministicCalculation() throws Exception {
        postEstimate(passengerToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.distanceFare").value(600.0))
                .andExpect(jsonPath("$.estimatedFare").value(750.0))
                .andExpect(jsonPath("$.currency").value("LKR"));
    }

    @Test
    void internalKeyCreatesFinalFare() throws Exception {
        postFinalWithInternal(INTERNAL_KEY).andExpect(status().isCreated());
    }

    @Test
    void adminCreatesFinalFare() throws Exception {
        postFinalWithJwt(adminToken()).andExpect(status().isCreated());
    }

    @Test
    void passengerCannotCreateFinalFare() throws Exception {
        postFinalWithJwt(passengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void driverCannotCreateFinalFare() throws Exception {
        postFinalWithJwt(driverToken()).andExpect(status().isForbidden());
    }

    @Test
    void noAuthenticationCannotCreateFinalFare() throws Exception {
        mockMvc.perform(post("/api/fares/final")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finalFareJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalKeyRetrievesFinalFareById() throws Exception {
        mockMvc.perform(get("/api/fares/final/fare-1")
                        .header("X-Internal-Service-Key", INTERNAL_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void internalKeyRetrievesFinalFareByRide() throws Exception {
        mockMvc.perform(get("/api/fares/final/ride/ride-1")
                        .header("X-Internal-Service-Key", INTERNAL_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void adminRetrievesFinalFare() throws Exception {
        mockMvc.perform(get("/api/fares/final/fare-1")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk());
    }

    @Test
    void passengerCannotRetrieveFinalFare() throws Exception {
        mockMvc.perform(get("/api/fares/final/fare-1")
                        .header("Authorization", bearer(passengerToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void driverCannotRetrieveFinalFare() throws Exception {
        mockMvc.perform(get("/api/fares/final/ride/ride-1")
                        .header("Authorization", bearer(driverToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateFinalFareStillReturnsConflict() throws Exception {
        when(finalFareService.calculate(any(FinalFareRequest.class)))
                .thenThrow(new DuplicateFinalFareException());

        postFinalWithInternal(INTERNAL_KEY).andExpect(status().isConflict());
    }

    @Test
    void finalFareResponsePreservesCalculationResult() throws Exception {
        postFinalWithInternal(INTERNAL_KEY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.distanceFare").value(1000.0))
                .andExpect(jsonPath("$.finalFare").value(1150.0));
    }

    @Test
    void internalKeyCreatesPayment() throws Exception {
        postPaymentWithInternal(INTERNAL_KEY).andExpect(status().isCreated());
    }

    @Test
    void adminCreatesPayment() throws Exception {
        postPaymentWithJwt(adminToken()).andExpect(status().isCreated());
    }

    @Test
    void passengerCannotCreatePayment() throws Exception {
        postPaymentWithJwt(passengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void driverCannotCreatePayment() throws Exception {
        postPaymentWithJwt(driverToken()).andExpect(status().isForbidden());
    }

    @Test
    void noAuthenticationCannotCreatePayment() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicatePaymentStillReturnsConflict() throws Exception {
        when(paymentService.createPayment(any(CreatePaymentRequest.class)))
                .thenThrow(new DuplicatePaymentException("ride-1"));

        postPaymentWithInternal(INTERNAL_KEY).andExpect(status().isConflict());
    }

    @Test
    void ownerPassengerGetsPaymentById() throws Exception {
        getPaymentById(passengerToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedPassengerCannotGetPaymentById() throws Exception {
        getPaymentById(otherPassengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void driverCannotGetPayment() throws Exception {
        getPaymentById(driverToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminGetsPayment() throws Exception {
        getPaymentById(adminToken()).andExpect(status().isOk());
    }

    @Test
    void internalKeyGetsPayment() throws Exception {
        mockMvc.perform(get("/api/payments/payment-1")
                        .header("X-Internal-Service-Key", INTERNAL_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void ownerPassengerGetsPaymentByRide() throws Exception {
        getPaymentByRide(passengerToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedPassengerCannotGetPaymentByRide() throws Exception {
        getPaymentByRide(otherPassengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void internalKeyGetsPaymentByRide() throws Exception {
        mockMvc.perform(get("/api/payments/ride/ride-1")
                        .header("X-Internal-Service-Key", INTERNAL_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void ownerPassengerCompletesPendingPayment() throws Exception {
        completePaymentWithJwt(passengerToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedPassengerCannotCompletePayment() throws Exception {
        completePaymentWithJwt(otherPassengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void driverCannotCompletePayment() throws Exception {
        completePaymentWithJwt(driverToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCompletesPayment() throws Exception {
        completePaymentWithJwt(adminToken()).andExpect(status().isOk());
    }

    @Test
    void internalKeyCompletesPayment() throws Exception {
        mockMvc.perform(post("/api/payments/payment-1/complete")
                        .header("X-Internal-Service-Key", INTERNAL_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void repeatedCompletionStillReturnsConflict() throws Exception {
        when(paymentService.completePayment("payment-1"))
                .thenThrow(new InvalidPaymentStateException(PaymentStatus.COMPLETED, "completed"));

        completePaymentWithJwt(adminToken()).andExpect(status().isConflict());
    }

    @Test
    void failedPaymentCannotBeCompleted() throws Exception {
        when(paymentService.completePayment("payment-1"))
                .thenThrow(new InvalidPaymentStateException(PaymentStatus.FAILED, "completed"));

        completePaymentWithJwt(adminToken()).andExpect(status().isConflict());
    }

    @Test
    void ownerPassengerFailsPendingPayment() throws Exception {
        failPaymentWithJwt(passengerToken()).andExpect(status().isOk());
    }

    @Test
    void unrelatedPassengerCannotFailPayment() throws Exception {
        failPaymentWithJwt(otherPassengerToken()).andExpect(status().isForbidden());
    }

    @Test
    void driverCannotFailPayment() throws Exception {
        failPaymentWithJwt(driverToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminFailsPayment() throws Exception {
        failPaymentWithJwt(adminToken()).andExpect(status().isOk());
    }

    @Test
    void internalKeyFailsPayment() throws Exception {
        mockMvc.perform(post("/api/payments/payment-1/fail")
                        .header("X-Internal-Service-Key", INTERNAL_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void repeatedFailureStillReturnsConflict() throws Exception {
        when(paymentService.failPayment("payment-1"))
                .thenThrow(new InvalidPaymentStateException(PaymentStatus.FAILED, "failed"));

        failPaymentWithJwt(adminToken()).andExpect(status().isConflict());
    }

    @Test
    void completedPaymentCannotBeFailed() throws Exception {
        when(paymentService.failPayment("payment-1"))
                .thenThrow(new InvalidPaymentStateException(PaymentStatus.COMPLETED, "failed"));

        failPaymentWithJwt(adminToken()).andExpect(status().isConflict());
    }

    @Test
    void invalidInternalKeyReturnsUnauthorized() throws Exception {
        postFinalWithInternal("wrong-key").andExpect(status().isUnauthorized());
    }

    @Test
    void correctInternalKeyIsAccepted() throws Exception {
        postFinalWithInternal(INTERNAL_KEY).andExpect(status().isCreated());
    }

    @Test
    void internalKeyIsNotLogged(CapturedOutput output) throws Exception {
        postFinalWithInternal(INTERNAL_KEY).andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(output).doesNotContain(INTERNAL_KEY);
    }

    @Test
    void apiDocsRemainPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs/security-test"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUiRemainsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/security-test"))
                .andExpect(status().isOk());
    }

    @Test
    void missingAuthenticationDoesNotOfferHttpBasic() throws Exception {
        mockMvc.perform(get("/api/payments/payment-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void missingAuthenticationDoesNotRedirectToFormLogin() throws Exception {
        mockMvc.perform(get("/api/payments/payment-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    private ResultActions postEstimate(String token) throws Exception {
        return mockMvc.perform(post("/api/fares/estimate")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(estimateJson()));
    }

    private ResultActions postFinalWithJwt(String token) throws Exception {
        return mockMvc.perform(post("/api/fares/final")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(finalFareJson()));
    }

    private ResultActions postFinalWithInternal(String key) throws Exception {
        return mockMvc.perform(post("/api/fares/final")
                .header("X-Internal-Service-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(finalFareJson()));
    }

    private ResultActions postPaymentWithJwt(String token) throws Exception {
        return mockMvc.perform(post("/api/payments")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson()));
    }

    private ResultActions postPaymentWithInternal(String key) throws Exception {
        return mockMvc.perform(post("/api/payments")
                .header("X-Internal-Service-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson()));
    }

    private ResultActions getPaymentById(String token) throws Exception {
        return mockMvc.perform(get("/api/payments/payment-1")
                .header("Authorization", bearer(token)));
    }

    private ResultActions getPaymentByRide(String token) throws Exception {
        return mockMvc.perform(get("/api/payments/ride/ride-1")
                .header("Authorization", bearer(token)));
    }

    private ResultActions completePaymentWithJwt(String token) throws Exception {
        return mockMvc.perform(post("/api/payments/payment-1/complete")
                .header("Authorization", bearer(token)));
    }

    private ResultActions failPaymentWithJwt(String token) throws Exception {
        return mockMvc.perform(post("/api/payments/payment-1/fail")
                .header("Authorization", bearer(token)));
    }

    private String estimateJson() {
        return """
                {"pickupLocation":"Colombo Fort","destinationLocation":"Bambalapitiya","distanceKm":7.5}
                """;
    }

    private String finalFareJson() {
        return """
                {"rideId":"ride-1","distanceKm":12.5}
                """;
    }

    private String paymentJson() {
        return """
                {"rideId":"ride-1","passengerId":"passenger-1","amount":1150.00,"method":"CARD"}
                """;
    }

    private FareEstimateResponse estimateResponse() {
        return new FareEstimateResponse(
                "estimate-1", "Colombo Fort", "Bambalapitiya",
                new BigDecimal("7.50"), new BigDecimal("150.00"),
                new BigDecimal("80.00"), new BigDecimal("600.00"),
                new BigDecimal("750.00"), "LKR", now());
    }

    private FinalFareResponse finalFareResponse() {
        return new FinalFareResponse(
                "fare-1", "ride-1", new BigDecimal("12.50"),
                new BigDecimal("150.00"), new BigDecimal("80.00"),
                new BigDecimal("1000.00"), new BigDecimal("1150.00"),
                "LKR", now());
    }

    private PaymentResponse paymentResponse(PaymentStatus status) {
        LocalDateTime completedAt = status == PaymentStatus.COMPLETED ? now() : null;
        return new PaymentResponse(
                "payment-1", "ride-1", OWNER, new BigDecimal("1150.00"),
                "LKR", PaymentMethod.CARD, status, now(), now(), completedAt);
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 10, 1, 12, 0);
    }

    private String passengerToken() {
        return activeJwt(OWNER, AccountRole.PASSENGER);
    }

    private String otherPassengerToken() {
        return activeJwt(OTHER_PASSENGER, AccountRole.PASSENGER);
    }

    private String driverToken() {
        return activeJwt("driver-1", AccountRole.DRIVER);
    }

    private String adminToken() {
        return activeJwt("admin-1", AccountRole.ADMIN);
    }

    private String activeJwt(String accountId, AccountRole role) {
        return jwt(accountId, role, AccountStatus.ACTIVE,
                Instant.now().plusSeconds(300), JWT_SECRET);
    }

    private String jwt(String accountId, AccountRole role, AccountStatus accountStatus,
                       Instant expiration, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(accountId)
                .claim("email", accountId + "@example.com")
                .claim("role", role.name())
                .claim("status", accountStatus.name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
