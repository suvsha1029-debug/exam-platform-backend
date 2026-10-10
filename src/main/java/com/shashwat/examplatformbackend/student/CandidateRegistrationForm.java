package com.shashwat.examplatformbackend.student;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public class CandidateRegistrationForm {
    @NotBlank
    @Size(max = 32)
    private String examCode;
    @Size(max = 32)
    private String streamCode;
    @NotBlank
    @Size(max = 120)
    private String fullName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @NotNull
    private LocalDate dateOfBirth;

    @Email
    @Size(max = 180)
    private String email;
    @Email
    @Size(max = 180)
    private String alternateEmail;
    @Size(max = 32)
    private String phone;
    @Size(max = 400)
    private String address;
    @Size(max = 80)
    private String state;
    @Size(max = 20)
    private String pincode;
    @Size(max = 80)
    private String nationality;
    @Size(max = 40)
    private String gender;
    @Size(max = 120)
    private String parentName;
    @Size(max = 32)
    private String parentPhone;
    @Size(max = 120)
    private String guardianName;
    @Size(max = 32)
    private String guardianPhone;
    @Size(max = 80)
    private String guardianRelation;
    @Size(max = 500)
    private String ugDetails;
    @Size(max = 500)
    private String pgDetails;
    @NotBlank
    @Size(max = 200)
    private String password;
    private MultipartFile photo;
    private MultipartFile signature;
    private MultipartFile idCard;
    private MultipartFile class10Marksheet;
    private MultipartFile class12Marksheet;
    private MultipartFile ugDocument;
    private MultipartFile pgDocument;

    public RegisterStudentRequest toRequest() {
        return new RegisterStudentRequest(
                examCode,
                streamCode,
                fullName,
                dateOfBirth,
                email,
                alternateEmail,
                phone,
                address,
                state,
                pincode,
                nationality,
                gender,
                parentName,
                parentPhone,
                guardianName,
                guardianPhone,
                guardianRelation,
                ugDetails,
                pgDetails,
                password
        );
    }

    public String getExamCode() {
        return examCode;
    }

    public void setExamCode(String examCode) {
        this.examCode = examCode;
    }

    public String getStreamCode() {
        return streamCode;
    }

    public void setStreamCode(String streamCode) {
        this.streamCode = streamCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAlternateEmail() {
        return alternateEmail;
    }

    public void setAlternateEmail(String alternateEmail) {
        this.alternateEmail = alternateEmail;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPincode() {
        return pincode;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public String getParentPhone() {
        return parentPhone;
    }

    public void setParentPhone(String parentPhone) {
        this.parentPhone = parentPhone;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public void setGuardianName(String guardianName) {
        this.guardianName = guardianName;
    }

    public String getGuardianPhone() {
        return guardianPhone;
    }

    public void setGuardianPhone(String guardianPhone) {
        this.guardianPhone = guardianPhone;
    }

    public String getGuardianRelation() {
        return guardianRelation;
    }

    public void setGuardianRelation(String guardianRelation) {
        this.guardianRelation = guardianRelation;
    }

    public String getUgDetails() {
        return ugDetails;
    }

    public void setUgDetails(String ugDetails) {
        this.ugDetails = ugDetails;
    }

    public String getPgDetails() {
        return pgDetails;
    }

    public void setPgDetails(String pgDetails) {
        this.pgDetails = pgDetails;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public MultipartFile getPhoto() {
        return photo;
    }

    public void setPhoto(MultipartFile photo) {
        this.photo = photo;
    }

    public MultipartFile getSignature() {
        return signature;
    }

    public void setSignature(MultipartFile signature) {
        this.signature = signature;
    }

    public MultipartFile getIdCard() {
        return idCard;
    }

    public void setIdCard(MultipartFile idCard) {
        this.idCard = idCard;
    }

    public MultipartFile getClass10Marksheet() {
        return class10Marksheet;
    }

    public void setClass10Marksheet(MultipartFile class10Marksheet) {
        this.class10Marksheet = class10Marksheet;
    }

    public MultipartFile getClass12Marksheet() {
        return class12Marksheet;
    }

    public void setClass12Marksheet(MultipartFile class12Marksheet) {
        this.class12Marksheet = class12Marksheet;
    }

    public MultipartFile getUgDocument() {
        return ugDocument;
    }

    public void setUgDocument(MultipartFile ugDocument) {
        this.ugDocument = ugDocument;
    }

    public MultipartFile getPgDocument() {
        return pgDocument;
    }

    public void setPgDocument(MultipartFile pgDocument) {
        this.pgDocument = pgDocument;
    }
}
