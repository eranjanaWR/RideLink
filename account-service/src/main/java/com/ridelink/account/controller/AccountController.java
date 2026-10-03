package com.ridelink.account.controller;

import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.dto.UpdateAccountProfileRequest;
import com.ridelink.account.dto.UpdateAccountStatusRequest;
import com.ridelink.account.exception.ApiError;
import com.ridelink.account.service.AccountManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {
    private final AccountManagementService accountManagementService;

    public AccountController(AccountManagementService accountManagementService) {
        this.accountManagementService = accountManagementService;
    }

    @Operation(summary = "Get account profile", description = "Available to the account owner or an ADMIN.")
    @ApiResponse(responseCode = "200", description = "Account profile",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #accountId")
    public AccountResponse getProfile(@PathVariable String accountId) {
        return accountManagementService.getProfile(accountId);
    }

    @Operation(summary = "Update account profile", description = "The owner or an ADMIN may update fullName and phone only.")
    @ApiResponse(responseCode = "200", description = "Updated account profile",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PutMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #accountId")
    public AccountResponse updateProfile(@PathVariable String accountId,
                                         @Valid @RequestBody UpdateAccountProfileRequest request) {
        return accountManagementService.updateProfile(accountId, request);
    }

    @Operation(summary = "Update account status", description = "Only an ADMIN may set ACTIVE, SUSPENDED, or DISABLED.")
    @ApiResponse(responseCode = "200", description = "Updated account status",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PatchMapping("/{accountId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse updateStatus(@PathVariable String accountId,
                                        @Valid @RequestBody UpdateAccountStatusRequest request) {
        return accountManagementService.updateStatus(accountId, request.status());
    }
}
