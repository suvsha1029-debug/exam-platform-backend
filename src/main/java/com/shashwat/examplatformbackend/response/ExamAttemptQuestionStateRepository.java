package com.shashwat.examplatformbackend.response;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamAttemptQuestionStateRepository extends JpaRepository<ExamAttemptQuestionState, Long> {
    long countByAttemptId(Long attemptId);

    List<ExamAttemptQuestionState> findAllByAttemptIdOrderByDisplayIndex(Long attemptId);

    Optional<ExamAttemptQuestionState> findByAttemptIdAndDisplayIndex(Long attemptId, int displayIndex);
}
