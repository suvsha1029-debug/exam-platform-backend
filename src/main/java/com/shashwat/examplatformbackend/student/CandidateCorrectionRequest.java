package com.shashwat.examplatformbackend.student;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CandidateCorrectionRequest(
        @NotBlank String registrationId,
        String password,
        String fullName,
        LocalDate dateOfBirth,
        String email,
        String alternateEmail,
        String phone,
        String address,
        String state,
        String pincode,
        String nationality,
        String gender,
        String parentName,
        String parentPhone,
        String guardianName,
        String guardianPhone,
        String guardianRelation,
        String ugDetails,
        String pgDetails
) {
}
