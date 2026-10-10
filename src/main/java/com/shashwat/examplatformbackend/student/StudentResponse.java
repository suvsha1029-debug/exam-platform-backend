package com.shashwat.examplatformbackend.student;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record StudentResponse(
        String registrationId,
        String examCode,
        String streamCode,
        String fullName,
        LocalDate dateOfBirth,
        String email,
        String phone,
        String state,
        String guardianName,
        String guardianPhone,
        String guardianRelation,
        String photoPath,
        String signaturePath,
        String idCardPath,
        String class10MarksheetPath,
        String class12MarksheetPath,
        String ugDocumentPath,
        String pgDocumentPath,
        Map<String, String> customFieldValues,
        Map<String, String> customFilePaths,
        Instant createdAt
) {
    static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getRegistrationId(),
                student.getExamCode(),
                student.getStreamCode(),
                student.getFullName(),
                student.getDateOfBirth(),
                student.getEmail(),
                student.getPhone(),
                student.getState(),
                student.getGuardianName(),
                student.getGuardianPhone(),
                student.getGuardianRelation(),
                student.getPhotoPath(),
                student.getSignaturePath(),
                student.getIdCardPath(),
                student.getClass10MarksheetPath(),
                student.getClass12MarksheetPath(),
                student.getUgDocumentPath(),
                student.getPgDocumentPath(),
                CandidateCustomDataMapper.toStringMap(student.getCustomFieldValuesJson()),
                CandidateCustomDataMapper.toStringMap(student.getCustomFilePathsJson()),
                student.getCreatedAt()
        );
    }
}
