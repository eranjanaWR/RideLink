package com.ridelink.account.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridelink.account.config.SecurityConfig;
import com.ridelink.account.security.SecurityErrorHandler;
import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.dto.LoginRequest;
import com.ridelink.account.dto.LoginResponse;
import com.ridelink.account.exception.AccountDisabledException;
import com.ridelink.account.exception.AccountSuspendedException;
import com.ridelink.account.exception.InvalidCredentialsException;
import com.ridelink.account.model.AccountRole;
import com.ridelink.account.model.AccountStatus;
import com.ridelink.account.service.AccountService;
import com.ridelink.account.service.AuthService;
import com.ridelink.account.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(value = AuthController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class LoginControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AccountService accountService;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;

    private ObjectNode validLogin() {
        ObjectNode body = mapper.createObjectNode();
        body.put("email", "dineth@example.com");
        body.put("password", "Password123");
        return body;
    }

    private AccountResponse account() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 30, 12, 0);
        return new AccountResponse("ea6d9f0a-3acf-418a-bb9e-d0cba33f79bd", "Dineth Perera",
                "dineth@example.com", "0771234567", AccountRole.PASSENGER, AccountStatus.ACTIVE, now, now);
    }

    private ResultActions login(String body) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void successfulLogin() {
        when(authService.login(any())).thenReturn(new LoginResponse("signed.jwt.token", "Bearer", 3600, account()));
    }

    private void badLogin(ObjectNode body) throws Exception {
        login(body.toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
        verifyNoInteractions(authService);
    }

    @Test void validLoginReturns200() throws Exception {
        successfulLogin(); login(validLogin().toString()).andExpect(status().isOk());
    }
    @Test void loginResponseUsesBearer() throws Exception {
        successfulLogin(); login(validLogin().toString()).andExpect(jsonPath("$.tokenType").value("Bearer"));
    }
    @Test void loginResponseContainsToken() throws Exception {
        successfulLogin(); login(validLogin().toString()).andExpect(jsonPath("$.accessToken").value("signed.jwt.token"));
    }
    @Test void loginResponseContainsAccount() throws Exception {
        successfulLogin(); login(validLogin().toString()).andExpect(jsonPath("$.account.email").value("dineth@example.com"));
    }
    @Test void loginResponseContainsExpirySeconds() throws Exception {
        successfulLogin(); login(validLogin().toString()).andExpect(jsonPath("$.expiresIn").value(3600));
    }
    @Test void loginResponseHasNoPassword() throws Exception {
        successfulLogin(); login(validLogin().toString()).andExpect(jsonPath("$.account.password").doesNotExist());
    }
    @Test void loginResponseHasNoPasswordHash() throws Exception {
        successfulLogin(); login(validLogin().toString()).andExpect(jsonPath("$.account.passwordHash").doesNotExist());
    }
    @Test void missingLoginEmailReturns400() throws Exception {
        ObjectNode body = validLogin(); body.remove("email"); badLogin(body);
    }
    @Test void invalidLoginEmailReturns400() throws Exception { badLogin(validLogin().put("email", "bad-email")); }
    @Test void missingLoginPasswordReturns400() throws Exception {
        ObjectNode body = validLogin(); body.remove("password"); badLogin(body);
    }
    @Test void blankLoginPasswordReturns400() throws Exception { badLogin(validLogin().put("password", "   ")); }
    @Test void loginWhitespaceEmailIsTrimmed() throws Exception {
        successfulLogin();
        login(validLogin().put("email", "  Dineth@Example.com  ").toString()).andExpect(status().isOk());
        org.mockito.ArgumentCaptor<LoginRequest> captor = org.mockito.ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Dineth@Example.com", captor.getValue().email());
    }
    @Test void invalidCredentialsReturn401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());
        login(validLogin().toString()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }
    @Test void suspendedAccountReturns403() throws Exception {
        when(authService.login(any())).thenThrow(new AccountSuspendedException());
        login(validLogin().toString()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is suspended"));
    }
    @Test void disabledAccountReturns403() throws Exception {
        when(authService.login(any())).thenThrow(new AccountDisabledException());
        login(validLogin().toString()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is disabled"));
    }
    @Test void malformedLoginJsonReturns400() throws Exception {
        login("{\"email\":").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Bad Request"));
    }
    @Test void unexpectedLoginFailureReturnsSanitized500() throws Exception {
        when(authService.login(any())).thenThrow(new IllegalStateException("secret details"));
        login(validLogin().toString()).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret details"))));
    }
    @Test void loginIsPublicAndHasNoFormRedirect() throws Exception {
        successfulLogin(); login(validLogin().toString()).andExpect(status().isOk()).andExpect(header().doesNotExist("Location"));
    }
    @Test void registerIsPublicAndHasNoBasicChallenge() throws Exception {
        when(accountService.register(any())).thenReturn(account());
        ObjectNode body = mapper.createObjectNode();
        body.put("fullName", "Dineth Perera"); body.put("email", "dineth@example.com");
        body.put("phone", "0771234567"); body.put("password", "Password123"); body.put("role", "PASSENGER");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("WWW-Authenticate"));
    }
    @Test void apiDocsAreNotBlockedBySecurity() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(result -> {
            org.junit.jupiter.api.Assertions.assertNotEquals(401, result.getResponse().getStatus());
            org.junit.jupiter.api.Assertions.assertNotEquals(403, result.getResponse().getStatus());
        });
    }
    @Test void swaggerUiIsNotBlockedBySecurity() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(result -> {
            org.junit.jupiter.api.Assertions.assertNotEquals(401, result.getResponse().getStatus());
            org.junit.jupiter.api.Assertions.assertNotEquals(403, result.getResponse().getStatus());
        });
    }
}
