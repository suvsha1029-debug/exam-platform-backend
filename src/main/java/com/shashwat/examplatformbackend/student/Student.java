package com.shashwat.examplatformbackend.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "students", indexes = {
        @Index(name = "idx_students_exam_code", columnList = "examCode")
})
public class Student {
    // Existing candidate rows have no hold when this nullable column is first introduced.
    private Boolean resultWithheld;

    public boolean isResultWithheld() { return Boolean.TRUE.equals(resultWithheld); }

    public void setResultWithheld(boolean withheld) { this.resultWithheld = withheld; }

    public void requireResultAvailable() {
        if (isResultWithheld()) {
            throw new IllegalStateException("Your result is withheld. Please contact the examination office.");
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 48)
    private String registrationId;

    @Column(nullable = false, length = 32)
    private String examCode;

    @Column(length = 24)
    private String streamCode;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    private String email;

    private String alternateEmail;

    private String phone;

    @Column(length = 600)
    private String address;

    @Column(length = 80)
    private String state;

    @Column(length = 12)
    private String pincode;

    @Column(length = 80)
    private String nationality;

    @Column(length = 40)
    private String gender;

    private String parentName;

    private String parentPhone;

    private String guardianName;

    private String guardianPhone;

    private String guardianRelation;

    @Column(length = 600)
    private String ugDetails;

    @Column(length = 600)
    private String pgDetails;

    private String photoPath;

    private String signaturePath;

    private String idCardPath;

    private String class10MarksheetPath;

    private String class12MarksheetPath;

    private String ugDocumentPath;

    private String pgDocumentPath;

    @Column(length = 8000)
    private String customFieldValuesJson;

    @Column(length = 8000)
    private String customFilePathsJson;

    @Column(nullable = false)
    private String passcodeHash;

    @Column(nullable = false)
    private Instant createdAt;

    protected Student() {
    }

    public Student(String registrationId, String examCode, String streamCode, RegisterStudentRequest request, String passcodeHash) {
        this.registrationId = registrationId;
        this.examCode = examCode;
        this.streamCode = streamCode;
        this.fullName = request.fullName().trim();
        this.dateOfBirth = request.dateOfBirth();
        this.email = clean(request.email());
        this.alternateEmail = clean(request.alternateEmail());
        this.phone = clean(request.phone());
        this.address = clean(request.address());
        this.state = clean(request.state());
        this.pincode = clean(request.pincode());
        this.nationality = clean(request.nationality());
        this.gender = clean(request.gender());
        this.parentName = clean(request.parentName());
        this.parentPhone = clean(request.parentPhone());
        this.guardianName = clean(request.guardianName());
        this.guardianPhone = clean(request.guardianPhone());
        this.guardianRelation = clean(request.guardianRelation());
        this.ugDetails = clean(request.ugDetails());
        this.pgDetails = clean(request.pgDetails());
        this.passcodeHash = passcodeHash;
        this.createdAt = Instant.now();
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public void attachFiles(StoredCandidateFiles files) {
        this.photoPath = files.photoPath();
        this.signaturePath = files.signaturePath();
        this.idCardPath = files.idCardPath();
        this.class10MarksheetPath = files.class10MarksheetPath();
        this.class12MarksheetPath = files.class12MarksheetPath();
        this.ugDocumentPath = files.ugDocumentPath();
        this.pgDocumentPath = files.pgDocumentPath();
    }

    public void attachCustomFields(String customFieldValuesJson, String customFilePathsJson) {
        this.customFieldValuesJson = clean(customFieldValuesJson);
        this.customFilePathsJson = clean(customFilePathsJson);
    }

    public void applyCorrection(CandidateCorrectionRequest request, java.util.Set<String> allowedFields) {
        if (allowedFields.contains("FULL_NAME")) this.fullName = correctExisting(this.fullName, request.fullName());
        if (allowedFields.contains("DATE_OF_BIRTH") && request.dateOfBirth() != null) this.dateOfBirth = request.dateOfBirth();
        if (allowedFields.contains("EMAIL")) this.email = correctExisting(this.email, request.email());
        if (allowedFields.contains("ALTERNATE_EMAIL")) this.alternateEmail = correctExisting(this.alternateEmail, request.alternateEmail());
        if (allowedFields.contains("PHONE")) this.phone = correctExisting(this.phone, request.phone());
        if (allowedFields.contains("ADDRESS")) this.address = correctExisting(this.address, request.address());
        if (allowedFields.contains("STATE")) this.state = correctExisting(this.state, request.state());
        if (allowedFields.contains("PINCODE")) this.pincode = correctExisting(this.pincode, request.pincode());
        if (allowedFields.contains("NATIONALITY")) this.nationality = correctExisting(this.nationality, request.nationality());
        if (allowedFields.contains("GENDER")) this.gender = correctExisting(this.gender, request.gender());
        if (allowedFields.contains("PARENT_NAME")) this.parentName = correctExisting(this.parentName, request.parentName());
        if (allowedFields.contains("PARENT_PHONE")) this.parentPhone = correctExisting(this.parentPhone, request.parentPhone());
        if (allowedFields.contains("GUARDIAN_NAME")) this.guardianName = correctExisting(this.guardianName, request.guardianName());
        if (allowedFields.contains("GUARDIAN_PHONE")) this.guardianPhone = correctExisting(this.guardianPhone, request.guardianPhone());
        if (allowedFields.contains("GUARDIAN_RELATION")) this.guardianRelation = correctExisting(this.guardianRelation, request.guardianRelation());
        if (allowedFields.contains("UG_DETAILS")) this.ugDetails = correctExisting(this.ugDetails, request.ugDetails());
        if (allowedFields.contains("PG_DETAILS")) this.pgDetails = correctExisting(this.pgDetails, request.pgDetails());
    }

    private static String correctExisting(String existing, String proposed) {
        return proposed == null ? existing : clean(proposed);
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getExamCode() {
        return examCode;
    }

    public String getStreamCode() {
        return streamCode;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getEmail() {
        return email;
    }

    public String getAlternateEmail() {
        return alternateEmail;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getPincode() {
        return pincode;
    }

    public String getState() {
        return state;
    }

    public String getNationality() {
        return nationality;
    }

    public String getGender() {
        return gender;
    }

    public String getParentName() {
        return parentName;
    }

    public String getParentPhone() {
        return parentPhone;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public String getGuardianPhone() {
        return guardianPhone;
    }

    public String getGuardianRelation() {
        return guardianRelation;
    }

    public String getUgDetails() {
        return ugDetails;
    }

    public String getPgDetails() {
        return pgDetails;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public String getSignaturePath() {
        return signaturePath;
    }

    public String getIdCardPath() {
        return idCardPath;
    }

    public String getClass10MarksheetPath() {
        return class10MarksheetPath;
    }

    public String getClass12MarksheetPath() {
        return class12MarksheetPath;
    }

    public String getUgDocumentPath() {
        return ugDocumentPath;
    }

    public String getPgDocumentPath() {
        return pgDocumentPath;
    }

    public String getCustomFieldValuesJson() {
        return customFieldValuesJson;
    }

    public String getCustomFilePathsJson() {
        return customFilePathsJson;
    }

    public String getPasscodeHash() {
        return passcodeHash;
    }

    public void changePasscodeHash(String passcodeHash) {
        if (passcodeHash == null || passcodeHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required.");
        }
        this.passcodeHash = passcodeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
