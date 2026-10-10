package com.shashwat.examplatformbackend.evaluation.manual;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ManualEvaluationGrantRepository extends JpaRepository<ManualEvaluationGrant, Long> {
    List<ManualEvaluationGrant> findAllByOrderByRequestedAtDesc();

    List<ManualEvaluationGrant> findAllByExamCodeOrderByRequestedAtDesc(String examCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grant from ManualEvaluationGrant grant where grant.id = :id")
    Optional<ManualEvaluationGrant> findByIdForUpdate(@Param("id") Long id);
}
