package com.shashwat.examplatformbackend.desktopconsole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "desktop_console_devices")
public class DesktopConsoleDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String deviceId;

    @Column(nullable = false, length = 180)
    private String deviceName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyBase64;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private Instant lastSeenAt;

    private Instant revokedAt;

    protected DesktopConsoleDevice() {
    }

    public DesktopConsoleDevice(String deviceId, String deviceName, String publicKeyBase64) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.publicKeyBase64 = publicKeyBase64;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getPublicKeyBase64() { return publicKeyBase64; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public void rotate(String deviceName, String publicKeyBase64) {
        this.deviceName = deviceName;
        this.publicKeyBase64 = publicKeyBase64;
        this.active = true;
        this.revokedAt = null;
        this.updatedAt = Instant.now();
    }

    public void touch() {
        this.lastSeenAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void revoke() {
        this.active = false;
        this.revokedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
