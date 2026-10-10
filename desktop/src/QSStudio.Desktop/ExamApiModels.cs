using System.Text.Json;
using System.Text.Json.Serialization;

namespace QSStudio.Desktop;

internal sealed record NativeExamSessionResponse(
    string RegistrationId,
    string ExamCode,
    string ExamName,
    string FullName,
    string PhotoDataUrl,
    string ExamUiHeaderTitle,
    string ExamUiHeaderSubtitle,
    string ExamUiBannerImageUrl,
    string InstituteLogoDataUrl,
    int RemainingSeconds,
    bool CandidateSubmitEnabled,
    bool DesktopCaptureProtectionEnabled,
    bool ExamReadinessTimerEnabled,
    int ExamReadinessDurationSeconds,
    string ExamSessionToken);

internal sealed record CandidateSecurityTokenResponse(string Token);

internal sealed record PublicExamResponse(
    string ExamCode,
    string Name,
    bool Active,
    bool RegistrationOpen,
    string PortalHeroImageUrl,
    string PortalTopBannerImageUrl,
    string ExamUiHeaderTitle,
    string ExamUiHeaderSubtitle,
    string ExamUiBannerImageUrl,
    bool DesktopEntryTimerEnabled,
    int DesktopEntryDelaySeconds,
    bool DesktopCaptureProtectionEnabled,
    bool ExamReadinessTimerEnabled,
    int ExamReadinessDurationSeconds);

internal sealed record BootstrapResponse(
    string ExamCode,
    string RegistrationId,
    int QuestionCount,
    DateTimeOffset DueAt,
    List<SectionSummary> Sections,
    List<QuestionSummary> Questions);

internal sealed record PaperResponse(
    string ExamCode,
    string RegistrationId,
    int QuestionCount,
    DateTimeOffset DueAt,
    List<SectionSummary> Sections,
    List<QuestionSummary> Questions,
    List<QuestionResponse> Paper);

internal sealed record SectionSummary(string Name, int StartDisplayIndex, int QuestionCount);

internal sealed record QuestionSummary(
    int DisplayIndex,
    string DisplayQuestionId,
    string Section,
    string Status,
    bool MarkedForReview);

internal sealed record QuestionResponse(
    int DisplayIndex,
    string DisplayQuestionId,
    string Section,
    string QuestionId,
    JsonElement Question,
    StateResponse State);

internal sealed record StateResponse(
    string Status,
    bool MarkedForReview,
    List<string> MarkedOptionIds,
    string NatAnswer,
    long TimeSpentMs,
    JsonElement? Activity,
    DateTimeOffset UpdatedAt);

internal sealed record AnswerResponse(StateResponse State, Dictionary<string, long> Counts);

internal sealed record ExamSubmitResponse(
    string RegistrationId,
    string ExamCode,
    string Status,
    int AnswerCount,
    DateTimeOffset SubmittedAt);

internal sealed record NativeOption(
    string Id,
    string Label,
    string Text,
    string ImageDataUrl);

internal sealed class ApiException : Exception
{
    internal ApiException(string message, int statusCode) : base(message)
    {
        StatusCode = statusCode;
    }

    internal int StatusCode { get; }
}

internal static class JsonDefaults
{
    internal static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };
}
