package com.shashwat.examplatformbackend.exam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shashwat.examplatformbackend.common.PageResponse;
import com.shashwat.examplatformbackend.evaluation.AnswerKeyStorageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ExamService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration OPEN_REGISTRATIONS_CACHE_TTL = Duration.ofSeconds(60);

    private final ExamRepository examRepository;
    private final PortalBrandingStorageService portalBrandingStorageService;
    private final QuestionPaperStorageService questionPaperStorageService;
    private final AnswerKeyStorageService answerKeyStorageService;
    private final ExamOriginalDocumentStorageService examOriginalDocumentStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile List<ExamPublicResponse> cachedOpenRegistrations = List.of();
    private volatile Instant cachedOpenRegistrationsAt = Instant.EPOCH;
    private volatile boolean cachedOpenRegistrationsLoaded = false;

    public ExamService(ExamRepository examRepository, PortalBrandingStorageService portalBrandingStorageService, QuestionPaperStorageService questionPaperStorageService, AnswerKeyStorageService answerKeyStorageService, ExamOriginalDocumentStorageService examOriginalDocumentStorageService) {
        this.examRepository = examRepository;
        this.portalBrandingStorageService = portalBrandingStorageService;
        this.questionPaperStorageService = questionPaperStorageService;
        this.answerKeyStorageService = answerKeyStorageService;
        this.examOriginalDocumentStorageService = examOriginalDocumentStorageService;
    }

    @Transactional
    public ExamResponse updatePostExamPublication(String examCode, PostExamPublicationRequest request) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found."));
        if (request.answerKeyChallengeEnabled() && (request.answerKeyChallengeOpensAt() == null
                || request.answerKeyChallengeClosesAt() == null
                || !request.answerKeyChallengeClosesAt().isAfter(request.answerKeyChallengeOpensAt()))) {
            throw new IllegalArgumentException("Set a valid opening and closing time for challenges.");
        }
        if (request.scoreCardPdfReleased() && !request.resultReleased()) {
            throw new IllegalArgumentException("Release results before enabling score-card downloads.");
        }
        if (request.originalQuestionPaperReleased() && exam.getOriginalQuestionPaperPdfPath() == null) {
            throw new IllegalArgumentException("Upload the question paper PDF before releasing it.");
        }
        if (request.originalAnswerKeyReleased() && exam.getOriginalAnswerKeyPdfPath() == null) {
            throw new IllegalArgumentException("Upload the answer key PDF before releasing it.");
        }
        exam.updatePostExamPublication(request);
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse create(CreateExamRequest request) {
        Exam exam = new Exam(
                newExamCode(),
                request.name().trim(),
                request.mode(),
                request.durationMinutes(),
                request.examStartAt()
        );
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> list() {
        return examRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ExamResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamPublicResponse> publicList() {
        return examRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(Exam::isActive)
                .map(ExamPublicResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamResponse> listPage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.from(
                examRepository.findAll(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .map(ExamResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public List<ExamPublicResponse> openRegistrations() {
        Instant now = Instant.now();
        if (cachedOpenRegistrationsLoaded && cachedOpenRegistrationsAt.plus(OPEN_REGISTRATIONS_CACHE_TTL).isAfter(now)) {
            return cachedOpenRegistrations;
        }
        List<ExamPublicResponse> fresh = examRepository.findAllByRegistrationOpenTrueOrderByCreatedAtDesc()
                .stream()
                .filter(Exam::isActive)
                .map(ExamPublicResponse::from)
                .toList();
        cachedOpenRegistrations = fresh;
        cachedOpenRegistrationsAt = now;
        cachedOpenRegistrationsLoaded = true;
        return fresh;
    }

    @Transactional
    public ExamResponse updateConfig(String examCode, UpdateExamConfigRequest request) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        if (request.answerKeyChallengeEnabled()) {
            if (request.answerKeyChallengeOpensAt() == null || request.answerKeyChallengeClosesAt() == null) {
                throw new IllegalArgumentException("Set both opening and closing times for the answer-key challenge window.");
            }
            if (!request.answerKeyChallengeClosesAt().isAfter(request.answerKeyChallengeOpensAt())) {
                throw new IllegalArgumentException("Answer-key challenge closing time must be after its opening time.");
            }
        }
        exam.updateRegistrationConfig(
                request.registrationOpen(),
                request.correctionsOpen(),
                request.streamSelectionRequired(),
                normalizeStreams(request.streams()),
                normalizeFields(request.enabledFields()),
                request.portalNotification(),
                request.portalNotifications(),
                request.portalNotificationBadgeStyle(),
                request.portalNoticeTabEnabled(),
                request.portalNoticeDocumentLabel(),
                request.portalWelcomeText(),
                request.portalHeaderTitle(),
                request.portalHeaderSubtitle(),
                request.portalHeaderTextColor(),
                request.portalHeaderBackgroundColor(),
                request.portalNotificationTextColor(),
                request.portalNotificationBackgroundColor(),
                request.portalNotificationFontSize(),
                request.portalHeroImageUrl(),
                request.portalTopBannerImageUrl(),
                request.portalPageBackgroundImageUrl(),
                request.portalWelcomeTextColor(),
                request.examUiHeaderTitle(),
                request.examUiHeaderSubtitle(),
                request.examUiBannerImageUrl(),
                request.questionSectionOrderJson(),
                request.questionMarkPriorityBySectionJson(),
                CustomCandidateFieldJson.stringify(normalizeCustomFields(request.customFields())),
                CustomCandidateFieldJson.stringifyStringMap(normalizeCandidateFieldSteps(request.candidateFieldSteps())),
                CustomCandidateFieldJson.stringifyStringMap(normalizeBooleanFieldMap(request.candidateRequiredFields(), normalizeFields(request.enabledFields()), true)),
                CustomCandidateFieldJson.stringifyStringMap(normalizeBooleanFieldMap(request.candidateCorrectionFields(), normalizeFields(request.enabledFields()), false)),
                CustomCandidateFieldJson.stringifyStringList(normalizeAllowedCountryIso2s(request.allowedCountryIso2s())),
                request.registrationEmailBody(),
                request.notificationSenderEmail(),
                request.notificationSenderAppPassword(),
                request.reportCardInstituteLogoDataUrl(),
                request.reportCardWatermarkLogoDataUrl(),
                request.reportCardAuthoritySignatureDataUrl(),
                request.reportCardAuthorityName(),
                request.reportCardAuthorityDesignation(),
                request.reportCardValidityText(),
                request.examStartAt(),
                request.examLobbyEnabled(),
                request.examLaunchEnabled(),
                request.candidateSubmitEnabled(),
                request.responsesReleased(),
                request.resultReleased(),
                request.originalQuestionPaperReleased(),
                request.originalAnswerKeyReleased(),
                request.scoreCardPdfReleased(),
                request.answerKeyChallengeEnabled(),
                request.answerKeyChallengeOpensAt(),
                request.answerKeyChallengeClosesAt(),
                request.optionRandomizationEnabled(),
                request.loginOpensMinutesBeforeStart(),
                request.loginClosesMinutesBeforeStart(),
                request.desktopEntryTimerEnabled() == null || request.desktopEntryTimerEnabled(),
                request.desktopEntryDelaySeconds() == null ? 120 : request.desktopEntryDelaySeconds(),
                request.desktopCaptureProtectionEnabled() == null || request.desktopCaptureProtectionEnabled(),
                request.examReadinessTimerEnabled() == null || request.examReadinessTimerEnabled(),
                request.examReadinessDurationSeconds() == null ? 120 : request.examReadinessDurationSeconds()
        );
        exam.updateReportCardAppearance(request.reportCardAppearance());
        exam.updatePortalSupportContent(request.portalSupportContent());
        exam.updatePortalNotices(request.portalNotices());
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse uploadPortalBanner(String examCode, MultipartFile file) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        String bannerUrl = portalBrandingStorageService.storePortalImage(exam.getExamCode(), "welcome", file);
        exam.updatePortalImageUrl("welcome", bannerUrl);
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse uploadPortalImage(String examCode, String placement, MultipartFile file) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        String normalizedPlacement = normalizePortalImagePlacement(placement);
        String imageUrl = portalBrandingStorageService.storePortalImage(exam.getExamCode(), normalizedPlacement, file);
        exam.updatePortalImageUrl(normalizedPlacement, imageUrl);
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse uploadQuestionPaper(String examCode, MultipartFile file) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        QuestionPaperStorageService.StoredQuestionPaper stored = questionPaperStorageService.store(exam.getExamCode(), file);
        if (exam.getAnswerKeyJsonPath() != null && !exam.getAnswerKeyJsonPath().isBlank()) {
            ensureMatchingPaperAnswerKeyAuth(
                    questionPaperStorageService.read(stored.path()),
                    answerKeyStorageService.read(exam.getAnswerKeyJsonPath())
            );
        }
        exam.attachQuestionPaper(stored.path(), stored.originalName(), stored.questionCount());
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse uploadAnswerKey(String examCode, MultipartFile file) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        AnswerKeyStorageService.StoredAnswerKey stored = answerKeyStorageService.store(exam.getExamCode(), file);
        if (exam.getQuestionPaperJsonPath() != null && !exam.getQuestionPaperJsonPath().isBlank()) {
            ensureMatchingPaperAnswerKeyAuth(
                    questionPaperStorageService.read(exam.getQuestionPaperJsonPath()),
                    answerKeyStorageService.read(stored.path())
            );
        }
        exam.attachAnswerKey(stored.path(), stored.originalName(), stored.totalMarks());
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse clearQuestionPaper(String examCode) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        questionPaperStorageService.deleteStored(exam.getQuestionPaperJsonPath());
        exam.clearQuestionPaper();
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse clearAnswerKey(String examCode) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        answerKeyStorageService.deleteStored(exam.getAnswerKeyJsonPath());
        exam.clearAnswerKey();
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional(readOnly = true)
    public JsonNode activeQuestionPaper(String examCode) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        return questionPaperStorageService.read(exam.getQuestionPaperJsonPath());
    }

    @Transactional(readOnly = true)
    public JsonNode activeAnswerKey(String examCode) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        return answerKeyStorageService.read(exam.getAnswerKeyJsonPath());
    }

    @Transactional(readOnly = true)
    public PaperKeyPairStatusResponse paperKeyPairStatus(String examCode) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        boolean paperUploaded = exam.getQuestionPaperJsonPath() != null && !exam.getQuestionPaperJsonPath().isBlank();
        boolean keyUploaded = exam.getAnswerKeyJsonPath() != null && !exam.getAnswerKeyJsonPath().isBlank();
        if (!paperUploaded || !keyUploaded) {
            String message = !paperUploaded && !keyUploaded
                    ? "No active question paper or answer key is attached."
                    : !paperUploaded
                    ? "Answer key is present, but no active question paper is attached."
                    : "Question paper is present, but no active answer key is attached.";
            return new PaperKeyPairStatusResponse(
                    exam.getExamCode(),
                    exam.getName(),
                    paperUploaded,
                    keyUploaded,
                    exam.getQuestionPaperOriginalName(),
                    exam.getAnswerKeyOriginalName(),
                    exam.getQuestionPaperQuestionCount() == null ? 0 : exam.getQuestionPaperQuestionCount(),
                    0,
                    exam.getAnswerKeyTotalMarks(),
                    false,
                    message,
                    "",
                    "",
                    List.of(),
                    List.of()
            );
        }
        JsonNode paper = questionPaperStorageService.read(exam.getQuestionPaperJsonPath());
        JsonNode key = answerKeyStorageService.read(exam.getAnswerKeyJsonPath());
        Set<String> paperQuestionIds = paperQuestionIds(paper);
        Set<String> keyQuestionIds = keyQuestionIds(key);
        LinkedHashSet<String> missingInKey = new LinkedHashSet<>(paperQuestionIds);
        missingInKey.removeAll(keyQuestionIds);
        LinkedHashSet<String> extraInKey = new LinkedHashSet<>(keyQuestionIds);
        extraInKey.removeAll(paperQuestionIds);
        String paperLicenseKey = paperLicenseKey(paper);
        String keyLicenseKey = keyLicenseKey(key);
        boolean licenseOk = paperLicenseKey.isBlank() || paperLicenseKey.equals(keyLicenseKey);
        boolean matched = licenseOk
                && !paperQuestionIds.isEmpty()
                && !keyQuestionIds.isEmpty()
                && missingInKey.isEmpty()
                && extraInKey.isEmpty();
        String message;
        if (matched) {
            message = "Active question paper and answer key are paired by stable question IDs.";
        } else if (paperQuestionIds.isEmpty()) {
            message = "Active question paper has no stable question IDs. Use an assembled/exported paper JSON.";
        } else if (keyQuestionIds.isEmpty()) {
            message = "Active answer key has no stable question IDs. Use the key generated for the paper JSON.";
        } else if (!licenseOk) {
            message = "Paper/key license keys differ. Clear stale files and upload the matching pair.";
        } else {
            message = "Paper/key question IDs do not match. Clear stale files or upload the matching assembled pair.";
        }
        return new PaperKeyPairStatusResponse(
                exam.getExamCode(),
                exam.getName(),
                true,
                true,
                exam.getQuestionPaperOriginalName(),
                exam.getAnswerKeyOriginalName(),
                paperQuestionIds.size(),
                keyQuestionIds.size(),
                exam.getAnswerKeyTotalMarks(),
                matched,
                message,
                paperLicenseKey,
                keyLicenseKey,
                new ArrayList<>(missingInKey),
                new ArrayList<>(extraInKey)
        );
    }

    @Transactional
    public ExamResponse uploadOriginalQuestionPaperPdf(String examCode, MultipartFile file) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        ExamOriginalDocumentStorageService.StoredDocument stored = examOriginalDocumentStorageService.storePdf(exam.getExamCode(), "question-paper-original", file);
        exam.attachOriginalQuestionPaperPdf(stored.path(), stored.originalName());
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse uploadOriginalAnswerKeyPdf(String examCode, MultipartFile file) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        ExamOriginalDocumentStorageService.StoredDocument stored = examOriginalDocumentStorageService.storePdf(exam.getExamCode(), "answer-key-original", file);
        exam.attachOriginalAnswerKeyPdf(stored.path(), stored.originalName());
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse uploadPortalNoticePdf(String examCode, MultipartFile file) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        ExamOriginalDocumentStorageService.StoredDocument stored = examOriginalDocumentStorageService.storePdf(exam.getExamCode(), "portal-notice", file);
        exam.attachPortalNoticePdf(stored.path(), stored.originalName());
        clearOpenRegistrationsCache();
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional(readOnly = true)
    public ExamOriginalDocumentStorageService.StoredDocumentData portalNoticePdf(String examCode) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        if (!exam.isPortalNoticeTabEnabled() || exam.getPortalNoticePdfPath() == null) {
            throw new IllegalArgumentException("Notice document is not available.");
        }
        return examOriginalDocumentStorageService.readPdf(exam.getPortalNoticePdfPath());
    }

    @Transactional(readOnly = true)
    public QuestionPaperMetadataResponse questionPaperMetadata(String examCode) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        return questionPaperStorageService.metadata(exam);
    }

    private static void ensureMatchingPaperAnswerKeyAuth(JsonNode paper, JsonNode key) {
        String paperLicenseKey = paperLicenseKey(paper);
        String keyLicenseKey = keyLicenseKey(key);
        if (!paperLicenseKey.isBlank() && !paperLicenseKey.equals(keyLicenseKey)) {
            throw new IllegalArgumentException("Answer key auth key does not match the uploaded question paper.");
        }
        Set<String> paperQuestionIds = paperQuestionIds(paper);
        Set<String> keyQuestionIds = keyQuestionIds(key);
        if (paperQuestionIds.isEmpty()) {
            throw new IllegalArgumentException("Question paper has no stable question IDs. Upload the assembled paper export.");
        }
        if (keyQuestionIds.isEmpty()) {
            throw new IllegalArgumentException("Answer key has no stable question IDs. Upload the key generated from the assembled paper.");
        }
        LinkedHashSet<String> missingInKey = new LinkedHashSet<>(paperQuestionIds);
        missingInKey.removeAll(keyQuestionIds);
        LinkedHashSet<String> extraInKey = new LinkedHashSet<>(keyQuestionIds);
        extraInKey.removeAll(paperQuestionIds);
        if (!missingInKey.isEmpty() || !extraInKey.isEmpty()) {
            throw new IllegalArgumentException("Answer key question IDs do not match the uploaded question paper. Missing in key: "
                    + previewIds(missingInKey) + "; extra in key: " + previewIds(extraInKey)
                    + ". Clear stale server paper/key and upload the matching assembled pair.");
        }
    }

    private static Set<String> paperQuestionIds(JsonNode paper) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        JsonNode questions = paper.path("questions");
        if (questions.isArray()) {
            for (JsonNode question : questions) {
                String id = firstNonBlank(
                        question.path("question_id").asText(""),
                        question.path("questionId").asText(""),
                        question.path("bank_question_id").asText(""),
                        question.path("bankQuestionId").asText(""),
                        question.path("source_question_id").asText(""),
                        question.path("sourceQuestionId").asText(""),
                        question.path("legacy_question_id").asText(""),
                        question.path("legacyQuestionId").asText(""),
                        question.path("export_question_alias").asText(""),
                        question.path("exportQuestionAlias").asText(""),
                        question.path("id").asText("")
                );
                if (!id.isBlank()) ids.add(id);
            }
        }
        return ids;
    }

    private static Set<String> keyQuestionIds(JsonNode key) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        JsonNode answers = key.path("answers");
        if (answers.isArray()) {
            for (JsonNode answer : answers) {
                String id = firstNonBlank(
                        answer.path("question_id").asText(""),
                        answer.path("questionId").asText(""),
                        answer.path("bank_question_id").asText(""),
                        answer.path("bankQuestionId").asText(""),
                        answer.path("source_question_id").asText(""),
                        answer.path("sourceQuestionId").asText(""),
                        answer.path("legacy_question_id").asText(""),
                        answer.path("legacyQuestionId").asText(""),
                        answer.path("export_question_alias").asText(""),
                        answer.path("exportQuestionAlias").asText(""),
                        answer.path("id").asText("")
                );
                if (!id.isBlank()) ids.add(id);
            }
        }
        return ids;
    }

    private static String previewIds(Set<String> ids) {
        if (ids.isEmpty()) {
            return "-";
        }
        List<String> list = new ArrayList<>(ids);
        int limit = Math.min(6, list.size());
        String text = String.join(", ", list.subList(0, limit));
        return list.size() > limit ? text + " +" + (list.size() - limit) + " more" : text;
    }

    private static String paperLicenseKey(JsonNode paper) {
        return firstNonBlank(
                paper.path("license_key").asText(""),
                paper.path("paper_license_key").asText(""),
                paper.path("licenseKey").asText(""),
                paper.path("paperLicenseKey").asText("")
        );
    }

    private static String keyLicenseKey(JsonNode key) {
        return firstNonBlank(
                key.path("license_key").asText(""),
                key.path("paper_license_key").asText(""),
                key.path("licenseKey").asText(""),
                key.path("paperLicenseKey").asText(""),
                key.path("source_license_key").asText(""),
                key.path("sourceLicenseKey").asText(""),
                key.path("paired_paper_license_key").asText(""),
                key.path("pairedPaperLicenseKey").asText("")
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    @Transactional(readOnly = true)
    public ReportCardTemplateResponse reportCardTemplate(String examCode) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        List<ReportCardTemplateEntry> templates = readTemplateLibrary(exam.getReportCardTemplatesJson(), exam.getReportCardTemplateJson(), exam.getActiveReportCardTemplateId(), exam.getActiveReportCardTemplateName());
        String templatesJson = writeTemplateLibrary(templates);
        return new ReportCardTemplateResponse(
                exam.getExamCode(),
                exam.getReportCardTemplateJson(),
                exam.getReportCardTemplateJson() != null,
                exam.getActiveReportCardTemplateId(),
                exam.getActiveReportCardTemplateName(),
                templatesJson,
                templates.size()
        );
    }

    @Transactional
    public ReportCardTemplateResponse saveReportCardTemplate(String examCode, ReportCardTemplateRequest request) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        List<ReportCardTemplateEntry> templates = readTemplateLibrary(exam.getReportCardTemplatesJson(), exam.getReportCardTemplateJson(), exam.getActiveReportCardTemplateId(), exam.getActiveReportCardTemplateName());
        String templateId = normalizeTemplateId(request.templateId());
        String templateName = normalizeTemplateName(request.templateName(), templates.size() + 1);
        if (templateId == null) {
            templateId = "tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        boolean found = false;
        List<ReportCardTemplateEntry> updated = new ArrayList<>();
        for (ReportCardTemplateEntry entry : templates) {
            if (entry.templateId().equals(templateId)) {
                updated.add(new ReportCardTemplateEntry(templateId, templateName, request.templateJson(), Instant.now().toString()));
                found = true;
            } else {
                updated.add(entry);
            }
        }
        if (!found) {
            updated.add(0, new ReportCardTemplateEntry(templateId, templateName, request.templateJson(), Instant.now().toString()));
        }
        boolean activate = request.activate() == null || request.activate();
        String activeTemplateId = activate ? templateId : (exam.getActiveReportCardTemplateId() == null ? templateId : exam.getActiveReportCardTemplateId());
        ReportCardTemplateEntry activeTemplate = updated.stream()
                .filter(entry -> entry.templateId().equals(activeTemplateId))
                .findFirst()
                .orElse(updated.get(0));
        exam.attachReportCardTemplateLibrary(
                writeTemplateLibrary(updated),
                activeTemplate.templateId(),
                activeTemplate.templateName(),
                activeTemplate.templateJson()
        );
        Exam saved = examRepository.save(exam);
        return new ReportCardTemplateResponse(
                saved.getExamCode(),
                saved.getReportCardTemplateJson(),
                saved.getReportCardTemplateJson() != null,
                saved.getActiveReportCardTemplateId(),
                saved.getActiveReportCardTemplateName(),
                saved.getReportCardTemplatesJson(),
                updated.size()
        );
    }

    @Transactional
    public ReportCardTemplateResponse activateReportCardTemplate(String examCode, String templateId) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        List<ReportCardTemplateEntry> templates = readTemplateLibrary(exam.getReportCardTemplatesJson(), exam.getReportCardTemplateJson(), exam.getActiveReportCardTemplateId(), exam.getActiveReportCardTemplateName());
        ReportCardTemplateEntry activeTemplate = templates.stream()
                .filter(entry -> entry.templateId().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Report card template not found: " + templateId));
        exam.attachReportCardTemplateLibrary(
                writeTemplateLibrary(templates),
                activeTemplate.templateId(),
                activeTemplate.templateName(),
                activeTemplate.templateJson()
        );
        Exam saved = examRepository.save(exam);
        return new ReportCardTemplateResponse(
                saved.getExamCode(),
                saved.getReportCardTemplateJson(),
                saved.getReportCardTemplateJson() != null,
                saved.getActiveReportCardTemplateId(),
                saved.getActiveReportCardTemplateName(),
                saved.getReportCardTemplatesJson(),
                templates.size()
        );
    }

    @Transactional
    public void delete(String examCode) {
        Exam exam = examRepository.findByExamCode(examCode)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examCode));
        examRepository.delete(exam);
        clearOpenRegistrationsCache();
    }

    @Transactional
    public void deleteAll() {
        examRepository.deleteAll();
        clearOpenRegistrationsCache();
    }

    private void clearOpenRegistrationsCache() {
        cachedOpenRegistrations = List.of();
        cachedOpenRegistrationsAt = Instant.EPOCH;
        cachedOpenRegistrationsLoaded = false;
    }

    private static List<ExamStream> normalizeStreams(List<ExamStreamRequest> streams) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ExamStream> normalized = new ArrayList<>();
        for (ExamStreamRequest stream : streams) {
            String shortCode = stream == null ? "" : stream.shortCode().trim().toUpperCase(Locale.ROOT);
            String fullName = stream == null ? "" : stream.fullName().trim();
            if (!shortCode.isBlank() && !fullName.isBlank() && seen.add(shortCode)) {
                normalized.add(new ExamStream(shortCode, fullName, stream.enabled()));
            }
        }
        return normalized;
    }

    private List<ReportCardTemplateEntry> readTemplateLibrary(String rawLibraryJson, String fallbackTemplateJson, String activeTemplateId, String activeTemplateName) {
        List<ReportCardTemplateEntry> templates = new ArrayList<>();
        try {
            if (rawLibraryJson != null && !rawLibraryJson.isBlank()) {
                templates.addAll(objectMapper.readValue(rawLibraryJson, new TypeReference<List<ReportCardTemplateEntry>>() {
                }));
            }
        } catch (Exception ignored) {
        }
        if (templates.isEmpty() && fallbackTemplateJson != null && !fallbackTemplateJson.isBlank()) {
            templates.add(new ReportCardTemplateEntry(
                    activeTemplateId == null || activeTemplateId.isBlank() ? "tpl_default" : activeTemplateId,
                    activeTemplateName == null || activeTemplateName.isBlank() ? "Default score card" : activeTemplateName,
                    fallbackTemplateJson,
                    Instant.now().toString()
            ));
        }
        return templates.stream()
                .filter(entry -> entry != null && entry.templateJson() != null && !entry.templateJson().isBlank())
                .toList();
    }

    private String writeTemplateLibrary(List<ReportCardTemplateEntry> templates) {
        try {
            return objectMapper.writeValueAsString(templates);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not store report card template library.", exception);
        }
    }

    private static String normalizeTemplateId(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return null;
        }
        return templateId.trim();
    }

    private static String normalizeTemplateName(String templateName, int index) {
        if (templateName == null || templateName.isBlank()) {
            return "Score card template " + index;
        }
        return templateName.trim();
    }

    private record ReportCardTemplateEntry(
            String templateId,
            String templateName,
            String templateJson,
            String updatedAt
    ) {
    }

    private static List<CandidateField> normalizeFields(List<CandidateField> fields) {
        LinkedHashSet<CandidateField> unique = new LinkedHashSet<>(fields);
        unique.add(CandidateField.FULL_NAME);
        unique.add(CandidateField.DATE_OF_BIRTH);
        unique.add(CandidateField.EMAIL);
        unique.add(CandidateField.PHONE);
        unique.add(CandidateField.PASSWORD);
        return new ArrayList<>(unique);
    }

    private static Map<String, String> normalizeCandidateFieldSteps(Map<CandidateField, String> steps) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (CandidateField field : CandidateField.values()) {
            String rawStep = steps == null ? "" : steps.get(field);
            normalized.put(field.name(), normalizeBuiltInStep(field, rawStep));
        }
        return normalized;
    }

    private static Map<String, String> normalizeBooleanFieldMap(Map<CandidateField, Boolean> selected, List<CandidateField> enabledFields, boolean includeMandatory) {
        LinkedHashSet<CandidateField> enabled = new LinkedHashSet<>(enabledFields);
        Map<String, String> normalized = new LinkedHashMap<>();
        for (CandidateField field : CandidateField.values()) {
            boolean value = Boolean.TRUE.equals(selected == null ? null : selected.get(field));
            if (includeMandatory && isAlwaysRequired(field)) {
                value = true;
            }
            if (!enabled.contains(field)) {
                value = false;
            }
            normalized.put(field.name(), Boolean.toString(value));
        }
        return normalized;
    }

    private static boolean isAlwaysRequired(CandidateField field) {
        return field == CandidateField.FULL_NAME
                || field == CandidateField.DATE_OF_BIRTH
                || field == CandidateField.EMAIL
                || field == CandidateField.PHONE
                || field == CandidateField.PASSWORD;
    }

    private static String normalizeBuiltInStep(CandidateField field, String rawStep) {
        String step = rawStep == null ? "" : rawStep.trim().toLowerCase(Locale.ROOT);
        if ("education".equals(step)) {
            return "education";
        }
        if ("uploads".equals(step)) {
            return "uploads";
        }
        if (field == CandidateField.UG_DETAILS || field == CandidateField.PG_DETAILS) {
            return "education";
        }
        if (field == CandidateField.PHOTO
                || field == CandidateField.SIGNATURE
                || field == CandidateField.ID_CARD
                || field == CandidateField.CLASS_10_MARKSHEET
                || field == CandidateField.CLASS_12_MARKSHEET) {
            return "uploads";
        }
        return "personal";
    }

    private static List<CustomCandidateField> normalizeCustomFields(List<CustomCandidateField> fields) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<CustomCandidateField> normalized = new ArrayList<>();
        for (CustomCandidateField field : fields == null ? List.<CustomCandidateField>of() : fields) {
            if (field == null) {
                continue;
            }
            String label = cleanText(field.label(), 120);
            String key = normalizeKey(field.key(), label);
            String step = normalizeStep(field.step());
            CustomCandidateFieldType type = field.type() == null ? CustomCandidateFieldType.TEXT : field.type();
            Integer maxSizeKb = normalizeMaxSizeKb(type, field.maxSizeKb());
            if (label != null && key != null && seen.add(key)) {
                normalized.add(new CustomCandidateField(key, label, type, field.required(), maxSizeKb, step, field.correctionAllowed()));
            }
        }
        return normalized;
    }

    private static String cleanText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static String normalizeKey(String rawKey, String label) {
        String source = rawKey == null || rawKey.isBlank() ? label : rawKey;
        if (source == null || source.isBlank()) {
            return null;
        }
        String normalized = source.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeStep(String rawStep) {
        String step = rawStep == null ? "" : rawStep.trim().toLowerCase(Locale.ROOT);
        if ("education".equals(step) || "uploads".equals(step)) {
            return step;
        }
        return "personal";
    }

    private static Integer normalizeMaxSizeKb(CustomCandidateFieldType type, Integer rawMaxSizeKb) {
        if (type == CustomCandidateFieldType.TEXT || type == CustomCandidateFieldType.TEXTAREA) {
            return null;
        }
        int defaultKb = type == CustomCandidateFieldType.IMAGE_UPLOAD ? 100 : 1024;
        if (rawMaxSizeKb == null) {
            return defaultKb;
        }
        return Math.max(1, Math.min(rawMaxSizeKb, 10 * 1024));
    }

    private static String normalizePortalImagePlacement(String placement) {
        if ("top-banner".equals(placement) || "page-background".equals(placement) || "welcome".equals(placement) || "exam-ui-banner".equals(placement)) {
            return placement;
        }
        throw new IllegalArgumentException("Unknown portal image placement.");
    }

    private static List<String> normalizeAllowedCountryIso2s(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String code = value.trim().toUpperCase(Locale.ROOT);
            if (code.matches("[A-Z]{2}")) {
                normalized.add(code);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String newExamCode() {
        String code;
        do {
            code = "EX" + Year.now().getValue() + randomToken(5);
        } while (examRepository.existsByExamCode(code));
        return code;
    }

    private static String randomToken(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }
}
