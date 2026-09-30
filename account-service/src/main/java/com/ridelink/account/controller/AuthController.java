package com.ridelink.account.controller;

import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.dto.RegisterAccountRequest;
import com.ridelink.account.exception.ApiError;
import com.ridelink.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Register an account",
            description = "Public registration supports PASSENGER and DRIVER only; ADMIN cannot self-register. "
                    + "Passwords are stored as BCrypt hashes and new accounts start ACTIVE.")
    @ApiResponse(responseCode = "201", description = "Registration successful",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request or ADMIN self-registration",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody RegisterAccountRequest request) {
        return accountService.register(request);
    }
}
