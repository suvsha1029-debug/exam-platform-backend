package com.shashwat.examplatformbackend.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExamAttemptSessionRepository extends JpaRepository<ExamAttemptSession, Long> {
    Optional<ExamAttemptSession> findByTokenHash(String tokenHash);

    List<ExamAttemptSession> findAllByExamCodeAndRegistrationIdAndSubmittedFalseAndRevokedFalseAndExpiresAtAfter(
            String examCode,
            String registrationId,
            Instant now
    );

    List<ExamAttemptSession> findAllBySubmittedFalseAndRevokedFalseOrderByIssuedAtDesc();

    List<ExamAttemptSession> findAllByExamCodeAndRegistrationIdOrderByIssuedAtDesc(String examCode, String registrationId);

    void deleteAllByRegistrationId(String registrationId);
}
