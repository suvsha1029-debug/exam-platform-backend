package com.shashwat.examplatformbackend.examaccess;

import com.shashwat.examplatformbackend.examaccess.ExamAccessDtos.CreateRequest;
import com.shashwat.examplatformbackend.examaccess.ExamAccessDtos.IncidentResponse;
import com.shashwat.examplatformbackend.examaccess.ExamAccessDtos.RequestResponse;
import com.shashwat.examplatformbackend.examaccess.ExamAccessDtos.ResumeContext;
import com.shashwat.examplatformbackend.response.ExamResponseDraftRepository;
import com.shashwat.examplatformbackend.response.ExamResponseSubmissionRepository;
import com.shashwat.examplatformbackend.student.ExamAttemptSession;
import com.shashwat.examplatformbackend.student.ExamAttemptSessionRepository;
import com.shashwat.examplatformbackend.student.ExamAttemptSessionService;
import com.shashwat.examplatformbackend.student.Student;
import com.shashwat.examplatformbackend.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ExamAccessService {
    private static final Duration STALE_HEARTBEAT = Duration.ofSeconds(60);
    private static final List<ExamAccessRequestStatus> OPEN_STATUSES = List.of(
            ExamAccessRequestStatus.PENDING_MASTER_APPROVAL,
            ExamAccessRequestStatus.APPROVED
    );

    private final ExamAccessRequestRepository requestRepository;
    private final ExamAttemptSessionRepository attemptRepository;
    private final ExamAttemptSessionService attemptService;
    private final StudentRepository studentRepository;
    private final ExamResponseSubmissionRepository submissionRepository;
    private final ExamResponseDraftRepository draftRepository;

    public ExamAccessService(ExamAccessRequestRepository requestRepository,
                             ExamAttemptSessionRepository attemptRepository,
                             ExamAttemptSessionService attemptService,
                             StudentRepository studentRepository,
                             ExamResponseSubmissionRepository submissionRepository,
                             ExamResponseDraftRepository draftRepository) {
        this.requestRepository = requestRepository;
        this.attemptRepository = attemptRepository;
        this.attemptService = attemptService;
        this.studentRepository = studentRepository;
        this.submissionRepository = submissionRepository;
        this.draftRepository = draftRepository;
    }

    @Transactional
    public List<IncidentResponse> incidents() {
        Instant staleBefore = Instant.now().minus(STALE_HEARTBEAT);
        return attemptRepository.findAllBySubmittedFalseAndRevokedFalseOrderByIssuedAtDesc().stream()
                .peek(attempt -> {
                    if (attempt.getInterruptedAt() == null
                            && attempt.getLastHeartbeatAt() != null
                            && attempt.getLastHeartbeatAt().isBefore(staleBefore)
                            && attempt.getDueAt().isAfter(Instant.now())) {
                        attempt.flagInterruption("Candidate heartbeat stopped before exam completion.");
                    }
                })
                .filter(attempt -> attempt.getInterruptedAt() != null)
                .map(this::incidentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> requests() {
        return requestRepository.findAllByOrderByRequestedAtDesc().stream().map(RequestResponse::from).toList();
    }

    @Transactional
    public RequestResponse create(CreateRequest request, String admin) {
        Student student = studentRepository.findByRegistrationId(request.registrationId().trim())
                .filter(row -> row.getExamCode().equals(request.examCode().trim()))
                .orElseThrow(() -> new IllegalArgumentException("Candidate was not found in this exam."));
        if (requestRepository.existsByExamCodeAndRegistrationIdAndTypeAndStatusIn(
                student.getExamCode(), student.getRegistrationId(), request.type(), OPEN_STATUSES)) {
            throw new IllegalStateException("An active " + request.type().name().toLowerCase() + " request already exists for this candidate.");
        }
        ExamAttemptSession attempt = latestAttempt(student);
        Integer remaining = null;
        Long attemptId = null;
        if (request.type() == ExamAccessRequestType.REJOIN) {
            if (attempt == null || attempt.getInterruptedAt() == null) {
                throw new IllegalStateException("Rejoin can only be requested for a flagged interrupted attempt.");
            }
            remaining = Math.max(60, attempt.getInterruptedRemainingSeconds() == null ? 60 : attempt.getInterruptedRemainingSeconds());
            attemptId = attempt.getId();
        } else if (!submissionRepository.existsByExamCodeAndRegistrationId(student.getExamCode(), student.getRegistrationId())
                && attempt == null) {
            throw new IllegalStateException("No response sheet or attempt exists to reset for this candidate.");
        }
        return RequestResponse.from(requestRepository.save(new ExamAccessRequest(
                student.getExamCode(), student.getRegistrationId(), student.getFullName(), request.type(),
                request.reason().trim(), remaining, attemptId, admin
        )));
    }

    @Transactional
    public RequestResponse approve(Long id, String reviewer, String note) {
        ExamAccessRequest request = find(id);
        request.approve(reviewer, note);
        return RequestResponse.from(request);
    }

    @Transactional
    public RequestResponse reject(Long id, String reviewer, String note) {
        ExamAccessRequest request = find(id);
        request.reject(reviewer, note);
        return RequestResponse.from(request);
    }

    @Transactional
    public RequestResponse apply(Long id, String admin) {
        ExamAccessRequest request = find(id);
        if (request.getType() == ExamAccessRequestType.REEXAM) {
            submissionRepository.findByExamCodeAndRegistrationId(request.getExamCode(), request.getRegistrationId())
                    .ifPresent(submissionRepository::delete);
            draftRepository.deleteAllByExamCodeAndRegistrationId(request.getExamCode(), request.getRegistrationId());
        }
        attemptService.revokeCandidateAttempts(request.getExamCode(), request.getRegistrationId());
        request.apply(admin);
        return RequestResponse.from(request);
    }

    @Transactional
    public ResumeContext consumeResumeContext(String examCode, String registrationId, int normalDurationSeconds) {
        return requestRepository
                .findFirstByExamCodeAndRegistrationIdAndTypeAndStatusAndConsumedAtIsNullOrderByAppliedAtDesc(
                        examCode, registrationId, ExamAccessRequestType.REJOIN, ExamAccessRequestStatus.APPLIED
                )
                .map(request -> {
                    request.consume();
                    String answers = draftRepository.findByExamCodeAndRegistrationId(examCode, registrationId)
                            .map(draft -> draft.getAnswersJson() == null ? "[]" : draft.getAnswersJson())
                            .orElse("[]");
                    return new ResumeContext(Math.max(60, request.getRemainingSeconds() == null ? 60 : request.getRemainingSeconds()), answers);
                })
                .orElseGet(() -> ResumeContext.fresh(normalDurationSeconds));
    }

    private ExamAttemptSession latestAttempt(Student student) {
        return attemptRepository.findAllByExamCodeAndRegistrationIdOrderByIssuedAtDesc(student.getExamCode(), student.getRegistrationId())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private IncidentResponse incidentResponse(ExamAttemptSession attempt) {
        String name = studentRepository.findByRegistrationId(attempt.getRegistrationId())
                .map(Student::getFullName)
                .orElse("Candidate");
        return new IncidentResponse(attempt.getId(), attempt.getExamCode(), attempt.getRegistrationId(), name,
                attempt.getInterruptedAt(), attempt.getInterruptedRemainingSeconds(), attempt.getInterruptionReason(),
                attempt.getLastHeartbeatAt());
    }

    private ExamAccessRequest find(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exam access request not found."));
    }
}
