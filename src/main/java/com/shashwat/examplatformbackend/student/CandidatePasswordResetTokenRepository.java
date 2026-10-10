package com.shashwat.examplatformbackend.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidatePasswordResetTokenRepository extends JpaRepository<CandidatePasswordResetToken, Long> {
    Optional<CandidatePasswordResetToken> findByTokenHash(String tokenHash);

    List<CandidatePasswordResetToken> findAllByStudentAndUsedAtIsNull(Student student);

    void deleteAllByStudent(Student student);
}
