package com.shashwat.examplatformbackend.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    boolean existsByExamCode(String examCode);

    Optional<Exam> findByExamCode(String examCode);

    List<Exam> findAllByOrderByCreatedAtDesc();

    List<Exam> findAllByRegistrationOpenTrueOrderByCreatedAtDesc();
}
