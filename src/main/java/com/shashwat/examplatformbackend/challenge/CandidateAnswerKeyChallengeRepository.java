package com.shashwat.examplatformbackend.challenge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateAnswerKeyChallengeRepository extends JpaRepository<CandidateAnswerKeyChallenge, Long> {
    List<CandidateAnswerKeyChallenge> findAllByExamCodeOrderByCreatedAtDesc(String examCode);
    List<CandidateAnswerKeyChallenge> findAllByExamCodeAndRegistrationIdOrderByCreatedAtDesc(String examCode, String registrationId);
    List<CandidateAnswerKeyChallenge> findAllByExamCodeAndQuestionIdAndStatus(String examCode, String questionId, CandidateAnswerKeyChallengeStatus status);
    boolean existsByExamCodeAndRegistrationIdAndQuestionId(String examCode, String registrationId, String questionId);
    boolean existsByExamCodeAndRegistrationIdAndQuestionIdAndStatus(String examCode, String registrationId, String questionId, CandidateAnswerKeyChallengeStatus status);
}
