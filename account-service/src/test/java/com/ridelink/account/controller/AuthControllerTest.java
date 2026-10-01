package com.ridelink.account.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ridelink.account.config.SecurityConfig;
import com.ridelink.account.security.SecurityErrorHandler;
import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.exception.DuplicateAccountException;
import com.ridelink.account.exception.InvalidRegistrationRoleException;
import com.ridelink.account.model.AccountRole;
import com.ridelink.account.model.AccountStatus;
import com.ridelink.account.service.AccountService;
import com.ridelink.account.service.AuthService;
import com.ridelink.account.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(value = AuthController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AccountService service;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;

    private ObjectNode valid() {
        ObjectNode body = mapper.createObjectNode();
        body.put("fullName", "Dineth Perera");
        body.put("email", "dineth@example.com");
        body.put("phone", "0771234567");
        body.put("password", "Password123");
        body.put("role", "PASSENGER");
        return body;
    }

    private AccountResponse response(AccountRole role) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 30, 12, 0);
        return new AccountResponse("ea6d9f0a-3acf-418a-bb9e-d0cba33f79bd", "Dineth Perera",
                "dineth@example.com", "0771234567", role, AccountStatus.ACTIVE, now, now);
    }

    private ResultActions submit(String body) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void badRequest(ObjectNode body) throws Exception {
        submit(body.toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
        verifyNoInteractions(service);
    }

    private void badRequestWithout(String field) throws Exception {
        ObjectNode body = valid();
        body.remove(field);
        badRequest(body);
    }

    @Test void validPassengerReturns201() throws Exception {
        when(service.register(any())).thenReturn(response(AccountRole.PASSENGER));
        submit(valid().toString()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PASSENGER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test void validDriverReturns201() throws Exception {
        when(service.register(any())).thenReturn(response(AccountRole.DRIVER));
        ObjectNode body = valid().put("role", "DRIVER");
        submit(body.toString()).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("DRIVER"));
    }

    @Test void surroundingWhitespaceIsAcceptedAndTrimmed() throws Exception {
        when(service.register(any())).thenReturn(response(AccountRole.PASSENGER));
        ObjectNode body = valid().put("fullName", "  Dineth Perera  ")
                .put("email", "  Dineth@Example.com  ").put("phone", "  0771234567  ");
        submit(body.toString()).andExpect(status().isCreated());
        org.mockito.ArgumentCaptor<com.ridelink.account.dto.RegisterAccountRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.ridelink.account.dto.RegisterAccountRequest.class);
        verify(service).register(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Dineth Perera", captor.getValue().fullName());
        org.junit.jupiter.api.Assertions.assertEquals("Dineth@Example.com", captor.getValue().email());
        org.junit.jupiter.api.Assertions.assertEquals("0771234567", captor.getValue().phone());
    }

    @Test void missingFullNameReturns400() throws Exception { badRequestWithout("fullName"); }
    @Test void blankFullNameReturns400() throws Exception { badRequest(valid().put("fullName", "   ")); }
    @Test void longFullNameReturns400() throws Exception { badRequest(valid().put("fullName", "A".repeat(101))); }
    @Test void invalidEmailReturns400() throws Exception { badRequest(valid().put("email", "not-an-email")); }
    @Test void missingEmailReturns400() throws Exception { badRequestWithout("email"); }
    @Test void longEmailReturns400() throws Exception { badRequest(valid().put("email", "a".repeat(250) + "@example.com")); }
    @Test void missingPhoneReturns400() throws Exception { badRequestWithout("phone"); }
    @Test void blankPhoneReturns400() throws Exception { badRequest(valid().put("phone", "   ")); }
    @Test void invalidPhoneReturns400() throws Exception { badRequest(valid().put("phone", "letters123")); }
    @Test void shortPasswordReturns400() throws Exception { badRequest(valid().put("password", "short7")); }
    @Test void missingPasswordReturns400() throws Exception { badRequestWithout("password"); }
    @Test void longPasswordReturns400() throws Exception { badRequest(valid().put("password", "P".repeat(101))); }
    @Test void missingRoleReturns400() throws Exception { badRequestWithout("role"); }

    @Test void adminRoleReturns400() throws Exception {
        when(service.register(any())).thenThrow(new InvalidRegistrationRoleException());
        submit(valid().put("role", "ADMIN").toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("PASSENGER and DRIVER")));
    }

    @Test void duplicateEmailReturns409() throws Exception {
        when(service.register(any())).thenThrow(new DuplicateAccountException());
        submit(valid().toString()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test void malformedJsonReturns400() throws Exception {
        submit("{\"fullName\":").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test void unsupportedRoleReturns400() throws Exception { badRequest(valid().put("role", "OWNER")); }

    @Test void responseHasNoPassword() throws Exception {
        when(service.register(any())).thenReturn(response(AccountRole.PASSENGER));
        submit(valid().toString()).andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test void responseHasNoPasswordHash() throws Exception {
        when(service.register(any())).thenReturn(response(AccountRole.PASSENGER));
        submit(valid().toString()).andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void responseIncludesIdentityAndTimestamps() throws Exception {
        when(service.register(any())).thenReturn(response(AccountRole.PASSENGER));
        submit(valid().toString()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ea6d9f0a-3acf-418a-bb9e-d0cba33f79bd"))
                .andExpect(jsonPath("$.email").value("dineth@example.com"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test void clientCannotSubmitStatus() throws Exception { badRequest(valid().put("status", "SUSPENDED")); }
    @Test void clientCannotSubmitId() throws Exception { badRequest(valid().put("id", "chosen")); }
    @Test void clientCannotSubmitPasswordHash() throws Exception { badRequest(valid().put("passwordHash", "chosen")); }
    @Test void clientCannotSubmitCreatedAt() throws Exception { badRequest(valid().put("createdAt", "2026-01-01T00:00:00")); }
    @Test void clientCannotSubmitUpdatedAt() throws Exception { badRequest(valid().put("updatedAt", "2026-01-01T00:00:00")); }

    @Test void unexpectedFailureReturnsSanitized500() throws Exception {
        when(service.register(any())).thenThrow(new IllegalStateException("database secrets"));
        submit(valid().toString()).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("database secrets"))));
    }
}
