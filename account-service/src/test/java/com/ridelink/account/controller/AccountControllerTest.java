package com.ridelink.account.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridelink.account.config.SecurityConfig;
import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.dto.LoginResponse;
import com.ridelink.account.dto.UpdateAccountProfileRequest;
import com.ridelink.account.exception.AccountNotFoundException;
import com.ridelink.account.model.Account;
import com.ridelink.account.model.AccountRole;
import com.ridelink.account.model.AccountStatus;
import com.ridelink.account.security.SecurityErrorHandler;
import com.ridelink.account.service.AccountManagementService;
import com.ridelink.account.service.AccountService;
import com.ridelink.account.service.AuthService;
import com.ridelink.account.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(value = {AccountController.class, AuthController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, AccountControllerTest.JwtTestConfig.class})
class AccountControllerTest {
    private static final String TEST_SECRET = "test-only-account-endpoint-secret-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwtService;
    @MockitoBean AccountManagementService managementService;
    @MockitoBean AccountService accountService;
    @MockitoBean AuthService authService;

    @TestConfiguration
    static class JwtTestConfig {
        @Bean JwtService jwtService() { return new JwtService(TEST_SECRET, 3_600_000); }
    }

    private Account account(String id, AccountRole role) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 12, 0);
        return new Account(id, "Test User", "test@example.com", "0771234567", "stored-hash",
                role, AccountStatus.ACTIVE, now, now);
    }

    private AccountResponse response(String id, AccountStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 12, 0);
        return new AccountResponse(id, "Test User", "test@example.com", "0771234567",
                AccountRole.PASSENGER, status, now, now);
    }

    private String bearer(String id, AccountRole role) {
        String token = jwtService.generateToken(account(id, role));
        assertTrue(jwtService.isTokenValid(token));
        return "Bearer " + token;
    }

    private ObjectNode profile() {
        return mapper.createObjectNode().put("fullName", "Updated User").put("phone", "+94 77 123 4567");
    }

    private ResultActions getProfile(String id, String authorization) throws Exception {
        return mvc.perform(get("/api/accounts/{accountId}", id).header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private ResultActions updateProfile(String id, String authorization, ObjectNode body) throws Exception {
        return mvc.perform(put("/api/accounts/{accountId}", id)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private ResultActions updateStatus(String id, String authorization, String body) throws Exception {
        return mvc.perform(patch("/api/accounts/{accountId}/status", id)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test void ownerCanGetOwnProfile() throws Exception {
        when(managementService.getProfile("owner")).thenReturn(response("owner", AccountStatus.ACTIVE));
        getProfile("owner", bearer("owner", AccountRole.PASSENGER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("owner"));
    }
    @Test void missingProfileReturns404() throws Exception {
        when(managementService.getProfile("missing")).thenThrow(new AccountNotFoundException());
        getProfile("missing", bearer("missing", AccountRole.PASSENGER)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }
    @Test void ownerCanUpdateOwnProfile() throws Exception {
        when(managementService.updateProfile(eq("owner"), any())).thenReturn(response("owner", AccountStatus.ACTIVE));
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile())
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("owner"));
    }
    @Test void blankFullNameReturns400() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile().put("fullName", "   "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(managementService);
    }
    @Test void blankPhoneReturns400() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile().put("phone", "   "))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(managementService);
    }
    @Test void missingFullNameReturns400() throws Exception {
        ObjectNode body = profile(); body.remove("fullName");
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), body).andExpect(status().isBadRequest());
    }
    @Test void missingPhoneReturns400() throws Exception {
        ObjectNode body = profile(); body.remove("phone");
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), body).andExpect(status().isBadRequest());
    }
    @Test void invalidPhoneReturns400() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile().put("phone", "letters123"))
                .andExpect(status().isBadRequest());
    }
    @Test void longFullNameReturns400() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile().put("fullName", "A".repeat(101)))
                .andExpect(status().isBadRequest());
    }
    @Test void profileUpdateTrimsInput() throws Exception {
        when(managementService.updateProfile(eq("owner"), any())).thenReturn(response("owner", AccountStatus.ACTIVE));
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER),
                profile().put("fullName", "  Updated User  ").put("phone", "  +94 77 123 4567  "))
                .andExpect(status().isOk());
        org.mockito.ArgumentCaptor<UpdateAccountProfileRequest> captor =
                org.mockito.ArgumentCaptor.forClass(UpdateAccountProfileRequest.class);
        verify(managementService).updateProfile(eq("owner"), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Updated User", captor.getValue().fullName());
        org.junit.jupiter.api.Assertions.assertEquals("+94 77 123 4567", captor.getValue().phone());
    }
    @Test void missingProfileUpdateReturns404() throws Exception {
        when(managementService.updateProfile(eq("missing"), any())).thenThrow(new AccountNotFoundException());
        updateProfile("missing", bearer("missing", AccountRole.PASSENGER), profile())
                .andExpect(status().isNotFound());
    }
    @Test void clientCannotUpdateEmail() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile().put("email", "changed@example.com"))
                .andExpect(status().isBadRequest());
    }
    @Test void clientCannotUpdateRole() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile().put("role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }
    @Test void clientCannotUpdateStatusThroughProfile() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile().put("status", "ACTIVE"))
                .andExpect(status().isBadRequest());
    }
    @Test void clientCannotUpdatePassword() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile().put("password", "changed"))
                .andExpect(status().isBadRequest());
    }
    @Test void clientCannotUpdatePasswordHash() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile().put("passwordHash", "changed"))
                .andExpect(status().isBadRequest());
    }
    @Test void clientCannotUpdateId() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile().put("id", "other"))
                .andExpect(status().isBadRequest());
    }
    @Test void clientCannotUpdateCreatedAt() throws Exception {
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER),
                profile().put("createdAt", "2020-01-01T00:00:00")).andExpect(status().isBadRequest());
    }
    @Test void adminCanSetActiveStatus() throws Exception {
        when(managementService.updateStatus("owner", AccountStatus.ACTIVE)).thenReturn(response("owner", AccountStatus.ACTIVE));
        updateStatus("owner", bearer("admin", AccountRole.ADMIN), "{\"status\":\"ACTIVE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }
    @Test void adminCanSetSuspendedStatus() throws Exception {
        when(managementService.updateStatus("owner", AccountStatus.SUSPENDED)).thenReturn(response("owner", AccountStatus.SUSPENDED));
        updateStatus("owner", bearer("admin", AccountRole.ADMIN), "{\"status\":\"SUSPENDED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
    }
    @Test void adminCanSetDisabledStatus() throws Exception {
        when(managementService.updateStatus("owner", AccountStatus.DISABLED)).thenReturn(response("owner", AccountStatus.DISABLED));
        updateStatus("owner", bearer("admin", AccountRole.ADMIN), "{\"status\":\"DISABLED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
    }
    @Test void missingStatusReturns400() throws Exception {
        updateStatus("owner", bearer("admin", AccountRole.ADMIN), "{}").andExpect(status().isBadRequest());
    }
    @Test void invalidStatusReturns400() throws Exception {
        updateStatus("owner", bearer("admin", AccountRole.ADMIN), "{\"status\":\"PAUSED\"}")
                .andExpect(status().isBadRequest());
    }
    @Test void missingStatusAccountReturns404() throws Exception {
        when(managementService.updateStatus("missing", AccountStatus.DISABLED)).thenThrow(new AccountNotFoundException());
        updateStatus("missing", bearer("admin", AccountRole.ADMIN), "{\"status\":\"DISABLED\"}")
                .andExpect(status().isNotFound());
    }
    @Test void getResponseNeverExposesPasswordOrHash() throws Exception {
        when(managementService.getProfile("owner")).thenReturn(response("owner", AccountStatus.ACTIVE));
        getProfile("owner", bearer("owner", AccountRole.PASSENGER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test void updateResponseNeverExposesPasswordOrHash() throws Exception {
        when(managementService.updateProfile(eq("owner"), any())).thenReturn(response("owner", AccountStatus.ACTIVE));
        updateProfile("owner", bearer("owner", AccountRole.PASSENGER), profile()).andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test void statusResponseNeverExposesPasswordOrHash() throws Exception {
        when(managementService.updateStatus("owner", AccountStatus.DISABLED)).thenReturn(response("owner", AccountStatus.DISABLED));
        updateStatus("owner", bearer("admin", AccountRole.ADMIN), "{\"status\":\"DISABLED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test void noTokenReturns401() throws Exception {
        mvc.perform(get("/api/accounts/owner")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/accounts/owner"));
    }
    @Test void invalidTokenReturns401() throws Exception {
        getProfile("owner", "Bearer invalid.jwt.token").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required or invalid token"));
    }
    @Test void malformedAuthorizationHeaderReturns401() throws Exception {
        getProfile("owner", "Basic credentials").andExpect(status().isUnauthorized());
    }
    @Test void expiredTokenReturns401() throws Exception {
        String token = Jwts.builder().subject("owner").claim("role", "PASSENGER")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        getProfile("owner", "Bearer " + token).andExpect(status().isUnauthorized());
    }
    @Test void tamperedTokenReturns401() throws Exception {
        String[] parts = bearer("owner", AccountRole.PASSENGER).substring(7).split("\\.");
        String payload = (parts[1].charAt(0) == 'A' ? "B" : "A") + parts[1].substring(1);
        getProfile("owner", "Bearer " + parts[0] + "." + payload + "." + parts[2])
                .andExpect(status().isUnauthorized());
    }
    @Test void ownerCanUpdateOwnProfileWithDriverRole() throws Exception {
        when(managementService.updateProfile(eq("owner"), any())).thenReturn(response("owner", AccountStatus.ACTIVE));
        updateProfile("owner", bearer("owner", AccountRole.DRIVER), profile()).andExpect(status().isOk());
    }
    @Test void passengerCannotGetAnotherProfile() throws Exception {
        getProfile("other", bearer("owner", AccountRole.PASSENGER)).andExpect(status().isForbidden());
        verifyNoInteractions(managementService);
    }
    @Test void passengerCannotUpdateAnotherProfile() throws Exception {
        updateProfile("other", bearer("owner", AccountRole.PASSENGER), profile())
                .andExpect(status().isForbidden());
        verifyNoInteractions(managementService);
    }
    @Test void adminCanGetAnotherProfile() throws Exception {
        when(managementService.getProfile("other")).thenReturn(response("other", AccountStatus.ACTIVE));
        getProfile("other", bearer("admin", AccountRole.ADMIN)).andExpect(status().isOk());
    }
    @Test void adminCanUpdateAnotherProfile() throws Exception {
        when(managementService.updateProfile(eq("other"), any())).thenReturn(response("other", AccountStatus.ACTIVE));
        updateProfile("other", bearer("admin", AccountRole.ADMIN), profile()).andExpect(status().isOk());
    }
    @Test void passengerCannotUpdateOwnStatus() throws Exception {
        updateStatus("owner", bearer("owner", AccountRole.PASSENGER), "{\"status\":\"ACTIVE\"}")
                .andExpect(status().isForbidden());
        verifyNoInteractions(managementService);
    }
    @Test void driverCannotUpdateStatus() throws Exception {
        updateStatus("owner", bearer("driver", AccountRole.DRIVER), "{\"status\":\"SUSPENDED\"}")
                .andExpect(status().isForbidden());
        verifyNoInteractions(managementService);
    }
    @Test void registerRemainsPublic() throws Exception {
        when(accountService.register(any())).thenReturn(response("new", AccountStatus.ACTIVE));
        ObjectNode body = mapper.createObjectNode().put("fullName", "Test User").put("email", "test@example.com")
                .put("phone", "0771234567").put("password", "Password123").put("role", "PASSENGER");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated());
    }
    @Test void loginRemainsPublic() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("token", "Bearer", 3600,
                response("owner", AccountStatus.ACTIVE)));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk());
    }
    @Test void swaggerEndpointsRemainPublic() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(result -> {
            assertNotEquals(401, result.getResponse().getStatus());
            assertNotEquals(403, result.getResponse().getStatus());
        });
        mvc.perform(get("/v3/api-docs")).andExpect(result -> {
            assertNotEquals(401, result.getResponse().getStatus());
            assertNotEquals(403, result.getResponse().getStatus());
        });
    }
    @Test void noBasicChallengeOnMissingToken() throws Exception {
        mvc.perform(get("/api/accounts/owner")).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }
    @Test void noFormLoginRedirectOnMissingToken() throws Exception {
        mvc.perform(get("/api/accounts/owner")).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }
    @Test void unexpectedServiceFailureIsSanitized() throws Exception {
        when(managementService.getProfile("owner")).thenThrow(new IllegalStateException("mongo internals"));
        getProfile("owner", bearer("owner", AccountRole.PASSENGER))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("mongo internals"))));
    }
}
