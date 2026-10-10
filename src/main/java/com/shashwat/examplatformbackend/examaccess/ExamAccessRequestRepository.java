package com.shashwat.examplatformbackend.examaccess;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamAccessRequestRepository extends JpaRepository<ExamAccessRequest, Long> {
    List<ExamAccessRequest> findAllByOrderByRequestedAtDesc();

    boolean existsByExamCodeAndRegistrationIdAndTypeAndStatusIn(
            String examCode,
            String registrationId,
            ExamAccessRequestType type,
            List<ExamAccessRequestStatus> statuses
    );

    Optional<ExamAccessRequest> findFirstByExamCodeAndRegistrationIdAndTypeAndStatusAndConsumedAtIsNullOrderByAppliedAtDesc(
            String examCode,
            String registrationId,
            ExamAccessRequestType type,
            ExamAccessRequestStatus status
    );

    void deleteAllByRegistrationId(String registrationId);
}
