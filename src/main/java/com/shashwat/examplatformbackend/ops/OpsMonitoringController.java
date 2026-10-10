package com.shashwat.examplatformbackend.ops;

import com.shashwat.examplatformbackend.student.ExamAttemptSession;
import com.shashwat.examplatformbackend.student.ExamAttemptSessionRepository;
import com.shashwat.examplatformbackend.student.Student;
import com.shashwat.examplatformbackend.student.StudentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class OpsMonitoringController {
    private final ExamAttemptSessionRepository sessionRepository;
    private final StudentRepository studentRepository;

    public OpsMonitoringController(ExamAttemptSessionRepository sessionRepository, StudentRepository studentRepository) {
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
    }

    @GetMapping("/api/admin/monitoring/sessions")
    public OpsSessionSnapshot sessions() {
        Instant now = Instant.now();
        List<ExamAttemptSession> sessions = sessionRepository.findAllBySubmittedFalseAndRevokedFalseOrderByIssuedAtDesc();
        Map<String, String> names = studentRepository.findAllByOrderByCreatedAtDesc().stream()
                .collect(Collectors.toMap(Student::getRegistrationId, Student::getFullName, (left, right) -> left));
        List<OpsSessionRow> rows = sessions.stream()
                .sorted(Comparator.comparing(ExamAttemptSession::getIssuedAt).reversed())
                .limit(200)
                .map(session -> row(session, names.getOrDefault(session.getRegistrationId(), "Candidate"), now))
                .toList();
        long active = rows.stream().filter(OpsSessionRow::active).count();
        return new OpsSessionSnapshot(active, rows.size(), now, rows);
    }

    private static OpsSessionRow row(ExamAttemptSession session, String candidateName, Instant now) {
        Instant dueAt = session.getDueAt();
        Instant heartbeat = Optional.ofNullable(session.getLastHeartbeatAt()).orElse(session.getIssuedAt());
        return new OpsSessionRow(
                session.getId(),
                session.getExamCode(),
                session.getRegistrationId(),
                candidateName,
                session.getIssuedAt(),
                dueAt,
                heartbeat,
                Math.max(0, Duration.between(now, dueAt).toSeconds()),
                session.isUsableAt(now),
                session.getInterruptedAt(),
                session.getInterruptionReason()
        );
    }

    public record OpsSessionSnapshot(long activeCount, int rowCount, Instant generatedAt, List<OpsSessionRow> rows) {
    }

    public record OpsSessionRow(
            Long attemptSessionId,
            String examCode,
            String registrationId,
            String candidateName,
            Instant issuedAt,
            Instant dueAt,
            Instant lastHeartbeatAt,
            long remainingSeconds,
            boolean active,
            Instant interruptedAt,
            String interruptionReason
    ) {
    }
}
