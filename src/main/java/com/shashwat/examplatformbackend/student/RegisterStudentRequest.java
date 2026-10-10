package com.shashwat.examplatformbackend.student;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterStudentRequest(
        @NotBlank
        @Size(max = 32)
        String examCode,
        @Size(max = 32)
        String streamCode,
        @NotBlank
        @Size(max = 120)
        String fullName,
        @NotNull LocalDate dateOfBirth,
        @Email
        @Size(max = 180)
        String email,
        @Email
        @Size(max = 180)
        String alternateEmail,
        @Size(max = 32)
        String phone,
        @Size(max = 400)
        String address,
        @Size(max = 80)
        String state,
        @Size(max = 20)
        String pincode,
        @Size(max = 80)
        String nationality,
        @Size(max = 40)
        String gender,
        @Size(max = 120)
        String parentName,
        @Size(max = 32)
        String parentPhone,
        @Size(max = 120)
        String guardianName,
        @Size(max = 32)
        String guardianPhone,
        @Size(max = 80)
        String guardianRelation,
        @Size(max = 500)
        String ugDetails,
        @Size(max = 500)
        String pgDetails,
        @NotBlank
        @Size(max = 200)
        String password
) {
}
