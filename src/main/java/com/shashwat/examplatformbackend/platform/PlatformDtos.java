package com.shashwat.examplatformbackend.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class PlatformDtos {
    private PlatformDtos() {
    }

    public record InstituteServiceRequestCreate(
            @NotBlank @Size(max = 180) String instituteName,
            @NotBlank @Size(max = 180) String contactName,
            @NotBlank @Email @Size(max = 190) String officialEmail,
            @NotBlank @Size(max = 32) String phoneNumber,
            @Size(max = 120) String designation,
            @Size(max = 500) String instituteAddress,
            @NotBlank @Size(max = 500) String requestedServices,
            @Size(max = 1000) String notes
    ) {
    }

    public record InstituteServiceRequestAction(
            @Size(max = 1000) String platformNotes
    ) {
    }

    public record InstituteServiceRequestResponse(
            Long id,
            String instituteName,
            String contactName,
            String officialEmail,
            String phoneNumber,
            String designation,
            String instituteAddress,
            String requestedServices,
            String notes,
            String status,
            String platformNotes,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PublicRequestResponse(Long id, String status, String message) {
    }
}
