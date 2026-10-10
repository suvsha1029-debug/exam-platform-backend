package com.shashwat.examplatformbackend.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shashwat.examplatformbackend.evaluation.AnswerKeyStorageService;
import com.shashwat.examplatformbackend.evaluation.EvaluationOverrideService;
import com.shashwat.examplatformbackend.evaluation.EvaluationOverrideType;
import com.shashwat.examplatformbackend.exam.Exam;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import com.shashwat.examplatformbackend.exam.QuestionPaperStorageService;
import com.shashwat.examplatformbackend.response.ExamResponseSubmissionRepository;
import com.shashwat.examplatformbackend.student.CandidateFileStorageService;
import com.shashwat.examplatformbackend.student.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateAnswerKeyChallengeResolutionTests {
    private static final String PNG_DATA_URL = "data:image/png;base64,iVBORw0KGgo=";

    @TempDir
    Path uploadRoot;

    @Test
    void markToAllCreatesGlobalScoringOverrideAndAcceptsMatchingChallenges() {
        Fixture fixture = fixture();
        CandidateAnswerKeyChallenge first = pending(11L, "APP-1");
        CandidateAnswerKeyChallenge second = pending(12L, "APP-2");
        when(fixture.repository.findById(11L)).thenReturn(Optional.of(first));
        when(fixture.repository.findAllByExamCodeAndQuestionIdAndStatus(
                "EXAM-1", "Q-GLOBAL-1", CandidateAnswerKeyChallengeStatus.PENDING))
                .thenReturn(List.of(first, second));

        fixture.service.resolve(
                11L,
                "exam-admin",
                CandidateAnswerKeyChallengeResolution.MARK_TO_ALL,
                "Published key is ambiguous."
        );

        verify(fixture.overrideService).replaceActive(
                "EXAM-1", "Q-GLOBAL-1", "Q.1", EvaluationOverrideType.MARK_TO_ALL,
                "Published key is ambiguous.", "exam-admin");
        verify(first).accept("exam-admin", "Published key is ambiguous.", CandidateAnswerKeyChallengeResolution.MARK_TO_ALL);
        verify(second).accept(
                "exam-admin",
                "Published key is ambiguous. (Applied globally after review of challenge #11.)",
                CandidateAnswerKeyChallengeResolution.MARK_TO_ALL
        );
    }

    @Test
    void keepCurrentKeyRejectsOnlyReviewedObjectionWithoutScoringOverride() {
        Fixture fixture = fixture();
        CandidateAnswerKeyChallenge row = pending(21L, "APP-1");
        when(fixture.repository.findById(21L)).thenReturn(Optional.of(row));

        fixture.service.resolve(
                21L,
                "exam-admin",
                CandidateAnswerKeyChallengeResolution.KEEP_CURRENT_KEY,
                "The published key is supported by the cited source."
        );

        verify(row).reject("exam-admin", "The published key is supported by the cited source.");
        verify(fixture.overrideService, never()).replaceActive(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void challengeSummaryRanksQuestionsAndCountsDistinctCandidates() {
        Fixture fixture = fixture();
        CandidateAnswerKeyChallenge first = challenge(31L, "Q-POPULAR", "Q.7", "APP-1", CandidateAnswerKeyChallengeStatus.PENDING, "2026-08-29T08:00:00Z");
        CandidateAnswerKeyChallenge duplicateCandidate = challenge(32L, "Q-POPULAR", "Q.7", "APP-1", CandidateAnswerKeyChallengeStatus.REJECTED, "2026-08-29T09:00:00Z");
        CandidateAnswerKeyChallenge secondCandidate = challenge(33L, "Q-POPULAR", "Q.7", "APP-2", CandidateAnswerKeyChallengeStatus.ACCEPTED, "2026-08-29T10:00:00Z");
        CandidateAnswerKeyChallenge other = challenge(34L, "Q-OTHER", "Q.2", "APP-3", CandidateAnswerKeyChallengeStatus.PENDING, "2026-08-29T11:00:00Z");
        when(fixture.repository.findAllByExamCodeOrderByCreatedAtDesc("EXAM-1"))
                .thenReturn(List.of(other, secondCandidate, duplicateCandidate, first));

        var summary = fixture.service.challengeSummary("EXAM-1");

        assertEquals(2, summary.size());
        assertEquals("Q-POPULAR", summary.getFirst().questionId());
        assertEquals(3, summary.getFirst().challengeCount());
        assertEquals(2, summary.getFirst().candidateCount());
        assertEquals(1, summary.getFirst().pendingCount());
        assertEquals(1, summary.getFirst().acceptedCount());
        assertEquals(1, summary.getFirst().rejectedCount());
        assertEquals(33L, summary.getFirst().latestChallengeId());
        assertEquals("Q-OTHER", summary.get(1).questionId());
    }

    @Test
    void challengeAssetsExposeSecureImageUrlsForCamelCaseArrayOptions() throws Exception {
        Fixture fixture = fixture();
        CandidateAnswerKeyChallenge row = pending(41L, "APP-1");
        Exam exam = mock(Exam.class);
        var paper = new ObjectMapper().readTree("""
                {"questions":[{
                  "question_id":"Q-GLOBAL-1",
                  "question_type":"MCQ",
                  "questionViewerImage":"%s",
                  "questionText":"Fallback question text",
                  "options":[
                    {"viewerImage":"%s","text":"Option A"},
                    {"text":"Option B"}
                  ]
                }]}
                """.formatted(PNG_DATA_URL, PNG_DATA_URL));
        when(fixture.repository.findById(41L)).thenReturn(Optional.of(row));
        when(fixture.examRepository.findByExamCode("EXAM-1")).thenReturn(Optional.of(exam));
        when(fixture.questionPaperStorageService.sanitizedPaper(exam)).thenReturn(paper);

        var assets = fixture.service.challengeAssets(41L);

        assertEquals("/api/evaluation/challenges/41/question-image",
                assets.servedQuestion().path("question_viewer_url").asText());
        assertFalse(assets.servedQuestion().has("question_viewer_image"));
        assertEquals("/api/evaluation/challenges/41/option-images/A",
                assets.servedQuestion().path("options").path("A").path("viewer_url").asText());
        assertEquals("Option B",
                assets.servedQuestion().path("options").path("B").path("selectable_text").asText());
        assertArrayEquals(java.util.Base64.getDecoder().decode("iVBORw0KGgo="), fixture.service.questionImage(41L).bytes());
        assertArrayEquals(java.util.Base64.getDecoder().decode("iVBORw0KGgo="), fixture.service.optionImage(41L, "A").bytes());
    }

    private Fixture fixture() {
        CandidateAnswerKeyChallengeRepository repository = mock(CandidateAnswerKeyChallengeRepository.class);
        EvaluationOverrideService overrideService = mock(EvaluationOverrideService.class);
        ExamRepository examRepository = mock(ExamRepository.class);
        QuestionPaperStorageService questionPaperStorageService = mock(QuestionPaperStorageService.class);
        CandidateAnswerKeyChallengeService service = new CandidateAnswerKeyChallengeService(
                repository,
                mock(StudentRepository.class),
                examRepository,
                mock(CandidateFileStorageService.class),
                questionPaperStorageService,
                mock(AnswerKeyStorageService.class),
                overrideService,
                mock(ExamResponseSubmissionRepository.class),
                uploadRoot.toString()
        );
        return new Fixture(service, repository, overrideService, examRepository, questionPaperStorageService);
    }

    private static CandidateAnswerKeyChallenge pending(Long id, String registrationId) {
        CandidateAnswerKeyChallenge row = mock(CandidateAnswerKeyChallenge.class);
        when(row.getId()).thenReturn(id);
        when(row.getExamCode()).thenReturn("EXAM-1");
        when(row.getQuestionId()).thenReturn("Q-GLOBAL-1");
        when(row.getDisplayQuestionId()).thenReturn("Q.1");
        when(row.getRegistrationId()).thenReturn(registrationId);
        when(row.getStatus()).thenReturn(CandidateAnswerKeyChallengeStatus.PENDING);
        return row;
    }

    private static CandidateAnswerKeyChallenge challenge(Long id,
                                                          String questionId,
                                                          String displayQuestionId,
                                                          String registrationId,
                                                          CandidateAnswerKeyChallengeStatus status,
                                                          String createdAt) {
        CandidateAnswerKeyChallenge row = mock(CandidateAnswerKeyChallenge.class);
        when(row.getId()).thenReturn(id);
        when(row.getExamCode()).thenReturn("EXAM-1");
        when(row.getQuestionId()).thenReturn(questionId);
        when(row.getDisplayQuestionId()).thenReturn(displayQuestionId);
        when(row.getQuestionNumber()).thenReturn(displayQuestionId);
        when(row.getRegistrationId()).thenReturn(registrationId);
        when(row.getStatus()).thenReturn(status);
        when(row.getCreatedAt()).thenReturn(Instant.parse(createdAt));
        return row;
    }

    private record Fixture(
            CandidateAnswerKeyChallengeService service,
            CandidateAnswerKeyChallengeRepository repository,
            EvaluationOverrideService overrideService,
            ExamRepository examRepository,
            QuestionPaperStorageService questionPaperStorageService
    ) {
    }
}
