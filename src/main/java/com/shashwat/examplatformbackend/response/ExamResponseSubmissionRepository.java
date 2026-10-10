package com.shashwat.examplatformbackend.response;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ExamResponseSubmissionRepository extends JpaRepository<ExamResponseSubmission, Long> {
    Optional<ExamResponseSubmission> findByExamCodeAndRegistrationId(String examCode, String registrationId);

    boolean existsByExamCodeAndRegistrationId(String examCode, String registrationId);

    void deleteAllByRegistrationId(String registrationId);

    List<ExamResponseSubmission> findAllByOrderBySubmittedAtDesc();

    Page<ExamResponseSubmission> findAllBy(Pageable pageable);

    List<ExamResponseSubmission> findAllByExamCodeOrderBySubmittedAtDesc(String examCode);

    Page<ExamResponseSubmission> findAllByExamCode(String examCode, Pageable pageable);
}
