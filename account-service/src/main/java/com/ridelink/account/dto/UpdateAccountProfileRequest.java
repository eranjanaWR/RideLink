package com.ridelink.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAccountProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 25) @Pattern(regexp = "[+0-9() .-]{7,25}") String phone) {
    public UpdateAccountProfileRequest {
        fullName = fullName == null ? null : fullName.trim();
        phone = phone == null ? null : phone.trim();
    }
}
