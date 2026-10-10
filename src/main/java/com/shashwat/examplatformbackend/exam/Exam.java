package com.shashwat.examplatformbackend.exam;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exams")
public class Exam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String examCode;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExamMode mode;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean registrationOpen;

    @Column(nullable = false)
    private boolean correctionsOpen;

    @Column(nullable = false)
    private boolean streamSelectionRequired;

    @Column(length = 600)
    private String portalNotification;

    @Column(length = 2400)
    private String portalNotifications;

    @Column(columnDefinition = "TEXT")
    private String portalNoticesJson;

    public List<PortalNotice> getPortalNotices() { return PortalNotice.parse(portalNoticesJson); }

    public void updatePortalNotices(List<PortalNotice> notices) {
        if (notices == null) return;
        if (notices.size() > 50 || notices.stream().anyMatch(n -> n == null || n.text().isBlank() || n.text().length() > 500)) {
            throw new IllegalArgumentException("Add up to 50 notices, each between 1 and 500 characters.");
        }
        var sorted = PortalNotice.ordered(notices);
        String legacy = sorted.stream().map(PortalNotice::text).collect(java.util.stream.Collectors.joining("\n"));
        if (legacy.length() > 2400) throw new IllegalArgumentException("Notifications must total no more than 2400 characters.");
        this.portalNoticesJson = PortalNotice.encode(sorted);
        this.portalNotifications = legacy;
        this.portalNotification = sorted.isEmpty() ? "" : sorted.getFirst().text();
    }

    @Column(length = 8)
    private String portalNotificationBadgeStyle;

    private Boolean portalNoticeTabEnabled;

    @Column(length = 160)
    private String portalNoticeDocumentLabel;

    @Column(length = 1200)
    private String portalNoticePdfPath;

    @Column(length = 240)
    private String portalNoticePdfName;

    @Column(length = 900)
    private String portalWelcomeText;

    @Column(columnDefinition = "TEXT")
    private String portalSupportContentJson;

    public String getPortalSupportContentJson() {
        return portalSupportContentJson;
    }

    public void updatePortalSupportContent(PortalSupportContent content) {
        if (content != null) this.portalSupportContentJson = content.toJson();
    }

    @Column(length = 220)
    private String portalHeaderTitle;

    @Column(length = 500)
    private String portalHeaderSubtitle;

    @Column(length = 16)
    private String portalHeaderTextColor;

    @Column(length = 16)
    private String portalHeaderBackgroundColor;

    @Column(length = 16)
    private String portalNotificationTextColor;

    @Column(length = 16)
    private String portalNotificationBackgroundColor;

    @Column(length = 16)
    private String portalNotificationFontSize;

    @Column(length = 1200)
    private String portalHeroImageUrl;

    @Column(length = 1200)
    private String portalTopBannerImageUrl;

    @Column(length = 1200)
    private String portalPageBackgroundImageUrl;

    @Column(length = 16)
    private String portalWelcomeTextColor;

    @Column(length = 220)
    private String examUiHeaderTitle;

    @Column(length = 500)
    private String examUiHeaderSubtitle;

    @Column(length = 1200)
    private String examUiBannerImageUrl;

    @Column(length = 1200)
    private String registrationEmailBody;

    @Column(length = 180)
    private String notificationSenderEmail;

    @Column(length = 300)
    private String notificationSenderAppPassword;

    @Column(columnDefinition = "TEXT")
    private String reportCardInstituteLogoDataUrl;

    @Column(columnDefinition = "TEXT")
    private String reportCardWatermarkLogoDataUrl;

    @Column(columnDefinition = "TEXT")
    private String reportCardAuthoritySignatureDataUrl;

    @Column(length = 220)
    private String reportCardAuthorityName;

    @Column(length = 220)
    private String reportCardAuthorityDesignation;

    @Column(length = 500)
    private String reportCardValidityText;

    @Column(columnDefinition = "TEXT")
    private String reportCardAppearanceJson;

    public String getReportCardAppearanceJson() {
        return reportCardAppearanceJson;
    }

    public void updateReportCardAppearance(ScoreCardAppearance appearance) {
        if (appearance != null) this.reportCardAppearanceJson = appearance.toJson();
    }

    @Column(length = 1200)
    private String questionPaperJsonPath;

    @Column(length = 240)
    private String questionPaperOriginalName;

    private Integer questionPaperQuestionCount;

    @Column(length = 1200)
    private String answerKeyJsonPath;

    @Column(length = 240)
    private String answerKeyOriginalName;

    private Double answerKeyTotalMarks;

    @Column(length = 1200)
    private String originalQuestionPaperPdfPath;

    @Column(length = 240)
    private String originalQuestionPaperPdfName;

    @Column(length = 1200)
    private String originalAnswerKeyPdfPath;

    @Column(length = 240)
    private String originalAnswerKeyPdfName;

    @Column(length = 2400)
    private String questionSectionOrderJson;

    @Column(length = 2400)
    private String questionMarkPriorityBySectionJson;

    @Column(length = 8000)
    private String customFieldsJson;

    @Column(length = 2400)
    private String candidateFieldStepConfigJson;

    @Column(length = 2400)
    private String candidateRequiredFieldsJson;

    @Column(length = 2400)
    private String candidateCorrectionFieldsJson;

    @Column(length = 2400)
    private String allowedCountryIso2sJson;

    @Column(columnDefinition = "TEXT")
    private String reportCardTemplateJson;

    @Column(columnDefinition = "TEXT")
    private String reportCardTemplatesJson;

    @Column(length = 160)
    private String activeReportCardTemplateId;

    @Column(length = 240)
    private String activeReportCardTemplateName;

    private Instant examStartAt;

    private Boolean examLobbyEnabled;

    private Boolean examLaunchEnabled;

    private Boolean candidateSubmitEnabled;

    private Boolean responsesReleased;

    private Boolean resultReleased;

    private Boolean originalQuestionPaperReleased;

    private Boolean originalAnswerKeyReleased;

    private Boolean scoreCardPdfReleased;

    private Boolean answerKeyChallengeEnabled;

    private Instant answerKeyChallengeOpensAt;

    private Instant answerKeyChallengeClosesAt;

    private Boolean optionRandomizationEnabled;

    private Integer loginOpensMinutesBeforeStart;

    private Integer loginClosesMinutesBeforeStart;

    private Boolean desktopEntryTimerEnabled;

    private Integer desktopEntryDelaySeconds;

    private Boolean desktopCaptureProtectionEnabled;

    private Boolean examReadinessTimerEnabled;

    private Integer examReadinessDurationSeconds;

    @ElementCollection
    @CollectionTable(name = "exam_streams", joinColumns = @JoinColumn(name = "exam_id"))
    private List<ExamStream> streams = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "exam_candidate_fields", joinColumns = @JoinColumn(name = "exam_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "field_name", nullable = false, length = 64)
    private List<CandidateField> enabledFields = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    protected Exam() {
    }

    public Exam(String examCode, String name, ExamMode mode, int durationMinutes, Instant examStartAt) {
        this.examCode = examCode;
        this.name = name;
        this.mode = mode;
        this.durationMinutes = durationMinutes;
        this.examStartAt = examStartAt;
        this.active = true;
        this.registrationOpen = false;
        this.correctionsOpen = false;
        this.portalNotificationBadgeStyle = "NEW";
        this.portalNoticeTabEnabled = false;
        this.streamSelectionRequired = false;
        this.examLobbyEnabled = false;
        this.examLaunchEnabled = false;
        this.candidateSubmitEnabled = false;
        this.responsesReleased = false;
        this.resultReleased = false;
        this.originalQuestionPaperReleased = false;
        this.originalAnswerKeyReleased = false;
        this.scoreCardPdfReleased = false;
        this.answerKeyChallengeEnabled = false;
        this.optionRandomizationEnabled = false;
        this.loginOpensMinutesBeforeStart = 30;
        this.loginClosesMinutesBeforeStart = 15;
        this.desktopEntryTimerEnabled = true;
        this.desktopEntryDelaySeconds = 120;
        this.desktopCaptureProtectionEnabled = true;
        this.examReadinessTimerEnabled = true;
        this.examReadinessDurationSeconds = 120;
        this.enabledFields = new ArrayList<>(List.of(
                CandidateField.FULL_NAME,
                CandidateField.DATE_OF_BIRTH,
                CandidateField.EMAIL,
                CandidateField.PHONE,
                CandidateField.PASSWORD
        ));
        this.createdAt = Instant.now();
    }

    public void updateRegistrationConfig(boolean registrationOpen, boolean correctionsOpen, boolean streamSelectionRequired, List<ExamStream> streams, List<CandidateField> enabledFields, String portalNotification, String portalNotifications, String portalNotificationBadgeStyle, boolean portalNoticeTabEnabled, String portalNoticeDocumentLabel, String portalWelcomeText, String portalHeaderTitle, String portalHeaderSubtitle, String portalHeaderTextColor, String portalHeaderBackgroundColor, String portalNotificationTextColor, String portalNotificationBackgroundColor, String portalNotificationFontSize, String portalHeroImageUrl, String portalTopBannerImageUrl, String portalPageBackgroundImageUrl, String portalWelcomeTextColor, String examUiHeaderTitle, String examUiHeaderSubtitle, String examUiBannerImageUrl, String questionSectionOrderJson, String questionMarkPriorityBySectionJson, String customFieldsJson, String candidateFieldStepConfigJson, String candidateRequiredFieldsJson, String candidateCorrectionFieldsJson, String allowedCountryIso2sJson, String registrationEmailBody, String notificationSenderEmail, String notificationSenderAppPassword, String reportCardInstituteLogoDataUrl, String reportCardWatermarkLogoDataUrl, String reportCardAuthoritySignatureDataUrl, String reportCardAuthorityName, String reportCardAuthorityDesignation, String reportCardValidityText, Instant examStartAt, boolean examLobbyEnabled, boolean examLaunchEnabled, boolean candidateSubmitEnabled, boolean responsesReleased, boolean resultReleased, boolean originalQuestionPaperReleased, boolean originalAnswerKeyReleased, boolean scoreCardPdfReleased, boolean answerKeyChallengeEnabled, Instant answerKeyChallengeOpensAt, Instant answerKeyChallengeClosesAt, boolean optionRandomizationEnabled, int loginOpensMinutesBeforeStart, int loginClosesMinutesBeforeStart, boolean desktopEntryTimerEnabled, int desktopEntryDelaySeconds, boolean desktopCaptureProtectionEnabled, boolean examReadinessTimerEnabled, int examReadinessDurationSeconds) {
        this.registrationOpen = registrationOpen;
        this.correctionsOpen = correctionsOpen;
        this.streamSelectionRequired = streamSelectionRequired;
        this.streams = new ArrayList<>(streams);
        this.enabledFields = new ArrayList<>(enabledFields);
        this.portalNotification = clean(portalNotification);
        this.portalNotifications = clean(portalNotifications);
        this.portalNotificationBadgeStyle = "OLD".equalsIgnoreCase(portalNotificationBadgeStyle) ? "OLD" : "NEW";
        this.portalNoticeTabEnabled = portalNoticeTabEnabled;
        this.portalNoticeDocumentLabel = clean(portalNoticeDocumentLabel);
        this.portalWelcomeText = clean(portalWelcomeText);
        this.portalHeaderTitle = clean(portalHeaderTitle);
        this.portalHeaderSubtitle = clean(portalHeaderSubtitle);
        this.portalHeaderTextColor = clean(portalHeaderTextColor);
        this.portalHeaderBackgroundColor = clean(portalHeaderBackgroundColor);
        this.portalNotificationTextColor = clean(portalNotificationTextColor);
        this.portalNotificationBackgroundColor = clean(portalNotificationBackgroundColor);
        this.portalNotificationFontSize = clean(portalNotificationFontSize);
        this.portalHeroImageUrl = clean(portalHeroImageUrl);
        this.portalTopBannerImageUrl = clean(portalTopBannerImageUrl);
        this.portalPageBackgroundImageUrl = clean(portalPageBackgroundImageUrl);
        this.portalWelcomeTextColor = clean(portalWelcomeTextColor);
        this.examUiHeaderTitle = clean(examUiHeaderTitle);
        this.examUiHeaderSubtitle = clean(examUiHeaderSubtitle);
        this.examUiBannerImageUrl = clean(examUiBannerImageUrl);
        this.questionSectionOrderJson = clean(questionSectionOrderJson);
        this.questionMarkPriorityBySectionJson = clean(questionMarkPriorityBySectionJson);
        this.customFieldsJson = clean(customFieldsJson);
        this.candidateFieldStepConfigJson = clean(candidateFieldStepConfigJson);
        this.candidateRequiredFieldsJson = clean(candidateRequiredFieldsJson);
        this.candidateCorrectionFieldsJson = clean(candidateCorrectionFieldsJson);
        this.allowedCountryIso2sJson = clean(allowedCountryIso2sJson);
        this.registrationEmailBody = clean(registrationEmailBody);
        this.notificationSenderEmail = clean(notificationSenderEmail);
        String cleanedAppPassword = cleanSecret(notificationSenderAppPassword);
        if (cleanedAppPassword != null) {
            this.notificationSenderAppPassword = cleanedAppPassword;
        }
        this.reportCardInstituteLogoDataUrl = cleanLarge(reportCardInstituteLogoDataUrl);
        this.reportCardWatermarkLogoDataUrl = cleanLarge(reportCardWatermarkLogoDataUrl);
        this.reportCardAuthoritySignatureDataUrl = cleanLarge(reportCardAuthoritySignatureDataUrl);
        this.reportCardAuthorityName = clean(reportCardAuthorityName);
        this.reportCardAuthorityDesignation = clean(reportCardAuthorityDesignation);
        this.reportCardValidityText = clean(reportCardValidityText);
        this.examStartAt = examStartAt;
        this.examLobbyEnabled = examLobbyEnabled;
        this.examLaunchEnabled = examLaunchEnabled;
        this.candidateSubmitEnabled = candidateSubmitEnabled;
        this.responsesReleased = responsesReleased;
        this.resultReleased = resultReleased;
        this.originalQuestionPaperReleased = originalQuestionPaperReleased;
        this.originalAnswerKeyReleased = originalAnswerKeyReleased;
        this.scoreCardPdfReleased = scoreCardPdfReleased;
        this.answerKeyChallengeEnabled = answerKeyChallengeEnabled;
        this.answerKeyChallengeOpensAt = answerKeyChallengeOpensAt;
        this.answerKeyChallengeClosesAt = answerKeyChallengeClosesAt;
        this.optionRandomizationEnabled = optionRandomizationEnabled;
        this.loginOpensMinutesBeforeStart = Math.max(0, loginOpensMinutesBeforeStart);
        this.loginClosesMinutesBeforeStart = Math.max(0, loginClosesMinutesBeforeStart);
        this.desktopEntryTimerEnabled = desktopEntryTimerEnabled;
        this.desktopEntryDelaySeconds = clampSeconds(desktopEntryDelaySeconds);
        this.desktopCaptureProtectionEnabled = desktopCaptureProtectionEnabled;
        this.examReadinessTimerEnabled = examReadinessTimerEnabled;
        this.examReadinessDurationSeconds = clampSeconds(examReadinessDurationSeconds);
    }

    public void updatePortalImageUrl(String placement, String url) {
        if ("top-banner".equals(placement)) {
            this.portalTopBannerImageUrl = clean(url);
        } else if ("page-background".equals(placement)) {
            this.portalPageBackgroundImageUrl = clean(url);
        } else if ("exam-ui-banner".equals(placement)) {
            this.examUiBannerImageUrl = clean(url);
        } else {
            this.portalHeroImageUrl = clean(url);
        }
    }

    public void attachQuestionPaper(String jsonPath, String originalName, int questionCount) {
        this.questionPaperJsonPath = clean(jsonPath);
        this.questionPaperOriginalName = clean(originalName);
        this.questionPaperQuestionCount = questionCount;
    }

    public void clearQuestionPaper() {
        this.questionPaperJsonPath = null;
        this.questionPaperOriginalName = null;
        this.questionPaperQuestionCount = null;
    }

    public void attachAnswerKey(String jsonPath, String originalName, double totalMarks) {
        this.answerKeyJsonPath = clean(jsonPath);
        this.answerKeyOriginalName = clean(originalName);
        this.answerKeyTotalMarks = totalMarks;
    }

    public void clearAnswerKey() {
        this.answerKeyJsonPath = null;
        this.answerKeyOriginalName = null;
        this.answerKeyTotalMarks = null;
    }

    public void attachOriginalQuestionPaperPdf(String path, String originalName) {
        this.originalQuestionPaperPdfPath = clean(path);
        this.originalQuestionPaperPdfName = clean(originalName);
    }

    public void attachOriginalAnswerKeyPdf(String path, String originalName) {
        this.originalAnswerKeyPdfPath = clean(path);
        this.originalAnswerKeyPdfName = clean(originalName);
    }

    public void attachPortalNoticePdf(String path, String originalName) {
        this.portalNoticePdfPath = clean(path);
        this.portalNoticePdfName = clean(originalName);
    }

    public void attachReportCardTemplateJson(String templateJson) {
        this.reportCardTemplateJson = cleanLarge(templateJson);
    }

    public void attachReportCardTemplateLibrary(String templatesJson, String activeTemplateId, String activeTemplateName, String activeTemplateJson) {
        this.reportCardTemplatesJson = cleanLarge(templatesJson);
        this.activeReportCardTemplateId = clean(activeTemplateId);
        this.activeReportCardTemplateName = clean(activeTemplateName);
        this.reportCardTemplateJson = cleanLarge(activeTemplateJson);
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String cleanLarge(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String cleanSecret(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String compact = value.replaceAll("\\s+", "").trim();
        return compact.isBlank() ? null : compact;
    }

    private static int clampSeconds(int value) {
        return Math.max(0, Math.min(3600, value));
    }

    public String getExamCode() {
        return examCode;
    }

    public String getName() {
        return name;
    }

    public ExamMode getMode() {
        return mode;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isRegistrationOpen() {
        return registrationOpen;
    }

    public boolean isCorrectionsOpen() {
        return correctionsOpen;
    }

    public boolean isStreamSelectionRequired() {
        return streamSelectionRequired;
    }

    public String getPortalNotification() {
        return portalNotification;
    }

    public String getPortalNotifications() {
        return portalNotifications;
    }

    public String getPortalNotificationBadgeStyle() {
        return "OLD".equalsIgnoreCase(portalNotificationBadgeStyle) ? "OLD" : "NEW";
    }

    public boolean isPortalNoticeTabEnabled() {
        return Boolean.TRUE.equals(portalNoticeTabEnabled);
    }

    public String getPortalNoticeDocumentLabel() {
        return portalNoticeDocumentLabel;
    }

    public String getPortalNoticePdfPath() {
        return portalNoticePdfPath;
    }

    public String getPortalNoticePdfName() {
        return portalNoticePdfName;
    }

    public String getPortalWelcomeText() {
        return portalWelcomeText;
    }

    public String getPortalHeaderTitle() {
        return portalHeaderTitle;
    }

    public String getPortalHeaderSubtitle() {
        return portalHeaderSubtitle;
    }

    public String getPortalHeaderTextColor() {
        return portalHeaderTextColor;
    }

    public String getPortalHeaderBackgroundColor() {
        return portalHeaderBackgroundColor;
    }

    public String getPortalNotificationTextColor() {
        return portalNotificationTextColor;
    }

    public String getPortalNotificationBackgroundColor() {
        return portalNotificationBackgroundColor;
    }

    public String getPortalNotificationFontSize() {
        return portalNotificationFontSize;
    }

    public String getPortalHeroImageUrl() {
        return portalHeroImageUrl;
    }

    public String getPortalTopBannerImageUrl() {
        return portalTopBannerImageUrl;
    }

    public String getPortalPageBackgroundImageUrl() {
        return portalPageBackgroundImageUrl;
    }

    public String getPortalWelcomeTextColor() {
        return portalWelcomeTextColor;
    }

    public String getExamUiHeaderTitle() {
        return examUiHeaderTitle;
    }

    public String getExamUiHeaderSubtitle() {
        return examUiHeaderSubtitle;
    }

    public String getExamUiBannerImageUrl() {
        return examUiBannerImageUrl;
    }

    public String getRegistrationEmailBody() {
        return registrationEmailBody;
    }

    public String getNotificationSenderEmail() {
        return notificationSenderEmail;
    }

    public String getNotificationSenderAppPassword() {
        return notificationSenderAppPassword;
    }

    public boolean hasNotificationSenderAppPassword() {
        return notificationSenderAppPassword != null && !notificationSenderAppPassword.isBlank();
    }

    public String getReportCardInstituteLogoDataUrl() {
        return reportCardInstituteLogoDataUrl;
    }

    public void updatePostExamPublication(PostExamPublicationRequest request) {
        this.responsesReleased = request.responsesReleased();
        this.resultReleased = request.resultReleased();
        this.scoreCardPdfReleased = request.scoreCardPdfReleased();
        this.originalQuestionPaperReleased = request.originalQuestionPaperReleased();
        this.originalAnswerKeyReleased = request.originalAnswerKeyReleased();
        this.answerKeyChallengeEnabled = request.answerKeyChallengeEnabled();
        this.answerKeyChallengeOpensAt = request.answerKeyChallengeOpensAt();
        this.answerKeyChallengeClosesAt = request.answerKeyChallengeClosesAt();
    }

    public String getReportCardWatermarkLogoDataUrl() {
        return reportCardWatermarkLogoDataUrl;
    }

    public String getReportCardAuthoritySignatureDataUrl() {
        return reportCardAuthoritySignatureDataUrl;
    }

    public String getReportCardAuthorityName() {
        return reportCardAuthorityName;
    }

    public String getReportCardAuthorityDesignation() {
        return reportCardAuthorityDesignation;
    }

    public String getReportCardValidityText() {
        return reportCardValidityText;
    }

    public String getQuestionPaperJsonPath() {
        return questionPaperJsonPath;
    }

    public String getQuestionPaperOriginalName() {
        return questionPaperOriginalName;
    }

    public Integer getQuestionPaperQuestionCount() {
        return questionPaperQuestionCount;
    }

    public String getAnswerKeyJsonPath() {
        return answerKeyJsonPath;
    }

    public String getAnswerKeyOriginalName() {
        return answerKeyOriginalName;
    }

    public Double getAnswerKeyTotalMarks() {
        return answerKeyTotalMarks;
    }

    public String getOriginalQuestionPaperPdfPath() {
        return originalQuestionPaperPdfPath;
    }

    public String getOriginalQuestionPaperPdfName() {
        return originalQuestionPaperPdfName;
    }

    public String getOriginalAnswerKeyPdfPath() {
        return originalAnswerKeyPdfPath;
    }

    public String getOriginalAnswerKeyPdfName() {
        return originalAnswerKeyPdfName;
    }

    public String getQuestionSectionOrderJson() {
        return questionSectionOrderJson;
    }

    public String getQuestionMarkPriorityBySectionJson() {
        return questionMarkPriorityBySectionJson;
    }

    public String getReportCardTemplateJson() {
        return reportCardTemplateJson;
    }

    public String getReportCardTemplatesJson() {
        return reportCardTemplatesJson;
    }

    public String getActiveReportCardTemplateId() {
        return activeReportCardTemplateId;
    }

    public String getActiveReportCardTemplateName() {
        return activeReportCardTemplateName;
    }

    public String getCustomFieldsJson() {
        return customFieldsJson;
    }

    public String getCandidateFieldStepConfigJson() {
        return candidateFieldStepConfigJson;
    }

    public String getCandidateRequiredFieldsJson() {
        return candidateRequiredFieldsJson;
    }

    public String getCandidateCorrectionFieldsJson() {
        return candidateCorrectionFieldsJson;
    }

    public String getAllowedCountryIso2sJson() {
        return allowedCountryIso2sJson;
    }

    public Instant getExamStartAt() {
        return examStartAt;
    }

    public boolean isExamLobbyEnabled() {
        return Boolean.TRUE.equals(examLobbyEnabled);
    }

    public boolean isExamLaunchEnabled() {
        return Boolean.TRUE.equals(examLaunchEnabled);
    }

    public boolean isCandidateSubmitEnabled() {
        return Boolean.TRUE.equals(candidateSubmitEnabled);
    }

    public boolean isResponsesReleased() {
        return Boolean.TRUE.equals(responsesReleased);
    }

    public boolean isResultReleased() {
        return Boolean.TRUE.equals(resultReleased);
    }

    public boolean isOriginalQuestionPaperReleased() {
        return Boolean.TRUE.equals(originalQuestionPaperReleased);
    }

    public boolean isOriginalAnswerKeyReleased() {
        return Boolean.TRUE.equals(originalAnswerKeyReleased);
    }

    public boolean isScoreCardPdfReleased() {
        return Boolean.TRUE.equals(scoreCardPdfReleased);
    }

    public boolean isAnswerKeyChallengeEnabled() {
        return Boolean.TRUE.equals(answerKeyChallengeEnabled);
    }

    public Instant getAnswerKeyChallengeOpensAt() {
        return answerKeyChallengeOpensAt;
    }

    public Instant getAnswerKeyChallengeClosesAt() {
        return answerKeyChallengeClosesAt;
    }

    public boolean isOptionRandomizationEnabled() {
        return Boolean.TRUE.equals(optionRandomizationEnabled);
    }

    public int getLoginOpensMinutesBeforeStart() {
        return loginOpensMinutesBeforeStart == null ? 30 : loginOpensMinutesBeforeStart;
    }

    public int getLoginClosesMinutesBeforeStart() {
        return loginClosesMinutesBeforeStart == null ? 15 : loginClosesMinutesBeforeStart;
    }

    public boolean isDesktopEntryTimerEnabled() {
        return desktopEntryTimerEnabled == null || Boolean.TRUE.equals(desktopEntryTimerEnabled);
    }

    public int getDesktopEntryDelaySeconds() {
        return desktopEntryDelaySeconds == null ? 120 : Math.max(0, desktopEntryDelaySeconds);
    }

    public boolean isDesktopCaptureProtectionEnabled() {
        return desktopCaptureProtectionEnabled == null || Boolean.TRUE.equals(desktopCaptureProtectionEnabled);
    }

    public boolean isExamReadinessTimerEnabled() {
        return examReadinessTimerEnabled == null || Boolean.TRUE.equals(examReadinessTimerEnabled);
    }

    public int getExamReadinessDurationSeconds() {
        return examReadinessDurationSeconds == null ? 120 : Math.max(0, examReadinessDurationSeconds);
    }

    public List<ExamStream> getStreams() {
        return streams;
    }

    public List<CandidateField> getEnabledFields() {
        return enabledFields;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
