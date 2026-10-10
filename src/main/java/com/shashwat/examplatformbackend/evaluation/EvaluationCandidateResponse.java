package com.shashwat.examplatformbackend.evaluation;

import com.shashwat.examplatformbackend.student.Student;
import com.shashwat.examplatformbackend.student.CandidateCustomDataMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record EvaluationCandidateResponse(
        String applicationNumber,
        String registrationId,
        String examCode,
        String examName,
        String streamCode,
        String fullName,
        LocalDate dateOfBirth,
        String email,
        String alternateEmail,
        String phone,
        String gender,
        String state,
        String nationality,
        Map<String, String> customFieldValues,
        Instant registeredAt
) {
    static EvaluationCandidateResponse from(Student student, String examName) {
        return new EvaluationCandidateResponse(
                student.getRegistrationId(),
                student.getRegistrationId(),
                student.getExamCode(),
                examName,
                student.getStreamCode(),
                student.getFullName(),
                student.getDateOfBirth(),
                student.getEmail(),
                student.getAlternateEmail(),
                student.getPhone(),
                student.getGender(),
                student.getState(),
                student.getNationality(),
                CandidateCustomDataMapper.toStringMap(student.getCustomFieldValuesJson()),
                student.getCreatedAt()
        );
    }
}
