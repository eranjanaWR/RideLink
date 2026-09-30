package com.ridelink.account.dto;

import com.ridelink.account.model.AccountRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterAccountRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 25) @Pattern(regexp = "[+0-9() .-]{7,25}") String phone,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotNull AccountRole role) {
    public RegisterAccountRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
        phone = phone == null ? null : phone.trim();
    }

    @Override
    public String toString() {
        return "RegisterAccountRequest[fullName=" + fullName + ", email=" + email
                + ", phone=" + phone + ", password=[REDACTED], role=" + role + "]";
    }
}
