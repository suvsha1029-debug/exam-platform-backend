package com.shashwat.examplatformbackend.desktopconsole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class DesktopConsoleDtos {
    private DesktopConsoleDtos() {
    }

    public record EnrollmentRequest(
            @NotBlank @Size(max = 80) String deviceId,
            @NotBlank @Size(max = 180) String deviceName,
            @NotBlank String publicKeyBase64
    ) {
    }

    public record DeviceResponse(
            Long id,
            String deviceId,
            String deviceName,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant lastSeenAt,
            Instant revokedAt
    ) {
        static DeviceResponse from(DesktopConsoleDevice device) {
            return new DeviceResponse(
                    device.getId(),
                    device.getDeviceId(),
                    device.getDeviceName(),
                    device.isActive(),
                    device.getCreatedAt(),
                    device.getUpdatedAt(),
                    device.getLastSeenAt(),
                    device.getRevokedAt()
            );
        }
    }
}
