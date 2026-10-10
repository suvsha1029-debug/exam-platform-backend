package com.shashwat.examplatformbackend.evaluation.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shashwat.examplatformbackend.common.JsonMapperFactory;
import com.shashwat.examplatformbackend.evaluation.EvaluationResultResponse;
import com.shashwat.examplatformbackend.evaluation.EvaluationService;
import com.shashwat.examplatformbackend.evaluation.manual.ManualEvaluationDtos.CreateRequest;
import com.shashwat.examplatformbackend.evaluation.manual.ManualEvaluationDtos.GrantResponse;
import com.shashwat.examplatformbackend.evaluation.manual.ManualEvaluationDtos.ReviewRequest;
import com.shashwat.examplatformbackend.evaluation.manual.ManualEvaluationDtos.RunResponse;
import com.shashwat.examplatformbackend.exam.ExamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class ManualEvaluationService {
    private static final long MAX_KEY_BYTES = 20L * 1024L * 1024L;
    private final ManualEvaluationGrantRepository repository;
    private final ExamRepository examRepository;
    private final EvaluationService evaluationService;
    private final ObjectMapper objectMapper = JsonMapperFactory.createLargeJsonMapper();

    public ManualEvaluationService(ManualEvaluationGrantRepository repository,
                                   ExamRepository examRepository,
                                   EvaluationService evaluationService) {
        this.repository = repository;
        this.examRepository = examRepository;
        this.evaluationService = evaluationService;
    }

    @Transactional
    public GrantResponse request(String examCode, CreateRequest request, String actor) {
        String cleanExamCode = requireExam(examCode);
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.length() < 12 || reason.length() > 800) {
            throw new IllegalArgumentException("Give a clear manual-evaluation reason between 12 and 800 characters.");
        }
        boolean activeExists = repository.findAllByExamCodeOrderByRequestedAtDesc(cleanExamCode).stream()
                .anyMatch(grant -> grant.getStatus() == ManualEvaluationGrantStatus.PENDING_MASTER_APPROVAL
                        || grant.isUsable(Instant.now()));
        if (activeExists) {
            throw new IllegalStateException("This exam already has a pending or usable manual evaluation request.");
        }
        return GrantResponse.from(repository.save(new ManualEvaluationGrant(cleanExamCode, reason, actor)));
    }

    @Transactional(readOnly = true)
    public List<GrantResponse> forExam(String examCode) {
        return repository.findAllByExamCodeOrderByRequestedAtDesc(requireExam(examCode)).stream()
                .map(GrantResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GrantResponse> all() {
        return repository.findAllByOrderByRequestedAtDesc().stream().map(GrantResponse::from).toList();
    }

    @Transactional
    public GrantResponse approve(Long id, ReviewRequest review, String actor) {
        ManualEvaluationGrant grant = locked(id);
        int minutes = review == null || review.validMinutes() == null ? 30 : review.validMinutes();
        if (minutes < 5 || minutes > 120) {
            throw new IllegalArgumentException("Manual evaluation approval must be valid for 5 to 120 minutes.");
        }
        grant.approve(actor, requireReviewNote(review), minutes);
        return GrantResponse.from(grant);
    }

    @Transactional
    public GrantResponse reject(Long id, ReviewRequest review, String actor) {
        ManualEvaluationGrant grant = locked(id);
        grant.reject(actor, requireReviewNote(review));
        return GrantResponse.from(grant);
    }

    @Transactional
    public RunResponse run(Long id, String examCode, MultipartFile file, String actor) {
        String cleanExamCode = requireExam(examCode);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose an answer key JSON file.");
        }
        if (file.getSize() > MAX_KEY_BYTES) {
            throw new IllegalArgumentException("Manual answer key exceeds the 20 MB safety limit.");
        }
        ManualEvaluationGrant grant = locked(id);
        grant.assertUsable(cleanExamCode, Instant.now());
        try {
            byte[] bytes = file.getBytes();
            JsonNode key = objectMapper.readTree(bytes);
            if (key == null || !key.isObject()) {
                throw new IllegalArgumentException("Manual answer key must be a JSON object.");
            }
            List<EvaluationResultResponse> results = evaluationService.manualResults(cleanExamCode, key);
            String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            grant.consume(actor, file.getOriginalFilename(), sha256, results.size());
            return new RunResponse(GrantResponse.from(grant), results.size(), results);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not read the manual answer key JSON.", exception);
        }
    }

    private String requireExam(String examCode) {
        String clean = examCode == null ? "" : examCode.trim();
        if (clean.isBlank() || !examRepository.existsByExamCode(clean)) {
            throw new IllegalArgumentException("Exam not found: " + clean);
        }
        return clean;
    }

    private ManualEvaluationGrant locked(Long id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Manual evaluation request not found."));
    }

    private static String requireReviewNote(ReviewRequest review) {
        String note = review == null || review.note() == null ? "" : review.note().trim();
        if (note.length() < 5 || note.length() > 800) {
            throw new IllegalArgumentException("Master-admin review note must be between 5 and 800 characters.");
        }
        return note;
    }
}
