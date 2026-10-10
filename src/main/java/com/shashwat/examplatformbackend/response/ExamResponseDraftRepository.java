package com.shashwat.examplatformbackend.response;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExamResponseDraftRepository extends JpaRepository<ExamResponseDraft, Long> {
    Optional<ExamResponseDraft> findByExamCodeAndRegistrationId(String examCode, String registrationId);

    void deleteAllByRegistrationId(String registrationId);

    void deleteAllByExamCodeAndRegistrationId(String examCode, String registrationId);
}
