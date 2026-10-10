package com.shashwat.examplatformbackend.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.time.Instant;

@Entity
@Table(name = "institute_service_requests", indexes = {
        @Index(name = "idx_institute_service_request_status", columnList = "status"),
        @Index(name = "idx_institute_service_request_created", columnList = "createdAt")
})
public class InstituteServiceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String instituteName;

    @Column(nullable = false, length = 180)
    private String contactName;

    @Column(nullable = false, length = 190)
    private String officialEmail;

    @Column(nullable = false, length = 32)
    private String phoneNumber;

    @Column(length = 120)
    private String designation;

    @Column(length = 500)
    private String instituteAddress;

    @Column(nullable = false, length = 500)
    private String requestedServices;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InstituteServiceRequestStatus status = InstituteServiceRequestStatus.PENDING;

    @Column(length = 1000)
    private String platformNotes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected InstituteServiceRequest() {
    }

    public InstituteServiceRequest(String instituteName, String contactName, String officialEmail, String phoneNumber,
                                   String designation, String instituteAddress, String requestedServices, String notes) {
        this.instituteName = instituteName;
        this.contactName = contactName;
        this.officialEmail = officialEmail;
        this.phoneNumber = phoneNumber;
        this.designation = designation;
        this.instituteAddress = instituteAddress;
        this.requestedServices = requestedServices;
        this.notes = notes;
    }

    public void updateStatus(InstituteServiceRequestStatus status, String platformNotes) {
        this.status = status;
        this.platformNotes = clean(platformNotes, 1000);
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getInstituteName() { return instituteName; }
    public String getContactName() { return contactName; }
    public String getOfficialEmail() { return officialEmail; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getDesignation() { return designation; }
    public String getInstituteAddress() { return instituteAddress; }
    public String getRequestedServices() { return requestedServices; }
    public String getNotes() { return notes; }
    public InstituteServiceRequestStatus getStatus() { return status; }
    public String getPlatformNotes() { return platformNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
