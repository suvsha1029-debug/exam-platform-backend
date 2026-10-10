package com.shashwat.examplatformbackend.masteradmin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class AdminAccountDtos {
    private AdminAccountDtos() {
    }

    public record AdminAccountResponse(
            Long id,
            String email,
            String adminName,
            String phoneNumber,
            LocalDate dateOfBirth,
            String examName,
            String serviceAvailed,
            String role,
            boolean active,
            boolean passwordSetupRequired,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record InviteAdminRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 160) String adminName,
            @NotBlank @Size(max = 32) String phoneNumber,
            @NotNull LocalDate dateOfBirth,
            @Size(max = 180) String examName,
            @Size(max = 500) String serviceAvailed
    ) {
    }

    public record PasswordSetupRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 160) String adminName,
            @NotBlank @Size(max = 32) String phoneNumber,
            @NotNull LocalDate dateOfBirth,
            @NotBlank @Size(min = 12, max = 120) String password
    ) {
    }

    public record PasswordSetupResponse(String email, String role, boolean ready, String message) {
    }
}
