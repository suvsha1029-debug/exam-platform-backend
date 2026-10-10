package com.shashwat.examplatformbackend.masteradmin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "admin_accounts")
public class AdminAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(nullable = false, length = 160)
    private String adminName;

    @Column(length = 32)
    private String phoneNumber;

    private LocalDate dateOfBirth;

    @Column(length = 180)
    private String examName;

    @Column(length = 500)
    private String serviceAvailed;

    @Column(length = 120)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AdminAccountRole role = AdminAccountRole.ADMIN;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean passwordSetupRequired = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected AdminAccount() {
    }

    public AdminAccount(String email, String adminName, AdminAccountRole role) {
        this.email = email;
        this.adminName = adminName;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getAdminName() { return adminName; }
    public String getPhoneNumber() { return phoneNumber; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getExamName() { return examName; }
    public String getServiceAvailed() { return serviceAvailed; }
    public String getPasswordHash() { return passwordHash; }
    public AdminAccountRole getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean isPasswordSetupRequired() { return passwordSetupRequired; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateProfile(String adminName, String phoneNumber, LocalDate dateOfBirth, String examName, String serviceAvailed) {
        this.adminName = adminName;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.examName = examName;
        this.serviceAvailed = serviceAvailed;
        this.updatedAt = Instant.now();
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordSetupRequired = false;
        this.updatedAt = Instant.now();
    }

    public void requirePasswordSetup() {
        this.passwordHash = null;
        this.passwordSetupRequired = true;
        this.updatedAt = Instant.now();
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }
}
