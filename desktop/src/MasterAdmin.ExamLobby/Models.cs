using System.Text.Json.Serialization;

namespace MasterAdmin.ExamLobby;

internal sealed record PageResponse<T>(
    [property: JsonPropertyName("content")] List<T> Content,
    [property: JsonPropertyName("page")] int Page,
    [property: JsonPropertyName("size")] int Size,
    [property: JsonPropertyName("totalElements")] long TotalElements,
    [property: JsonPropertyName("totalPages")] int TotalPages);

internal sealed record ExamRow(
    [property: JsonPropertyName("examCode")] string ExamCode,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("subject")] string? Subject,
    [property: JsonPropertyName("section")] string? Section,
    [property: JsonPropertyName("examLobbyEnabled")] bool ExamLobbyEnabled,
    [property: JsonPropertyName("examLaunchEnabled")] bool ExamLaunchEnabled,
    [property: JsonPropertyName("questionPaperUploaded")] bool QuestionPaperUploaded);

internal sealed record StudentRow(
    [property: JsonPropertyName("registrationId")] string RegistrationId,
    [property: JsonPropertyName("fullName")] string FullName,
    [property: JsonPropertyName("examCode")] string ExamCode,
    [property: JsonPropertyName("applicationNumber")] string? ApplicationNumber);

internal sealed record ResponseRow(
    [property: JsonPropertyName("registrationId")] string RegistrationId,
    [property: JsonPropertyName("candidateName")] string CandidateName,
    [property: JsonPropertyName("examCode")] string ExamCode,
    [property: JsonPropertyName("status")] string Status);

internal sealed record IncidentRow(
    [property: JsonPropertyName("attemptSessionId")] long AttemptSessionId,
    [property: JsonPropertyName("examCode")] string ExamCode,
    [property: JsonPropertyName("registrationId")] string RegistrationId,
    [property: JsonPropertyName("candidateName")] string CandidateName,
    [property: JsonPropertyName("interruptedAt")] DateTimeOffset? InterruptedAt,
    [property: JsonPropertyName("remainingSeconds")] int? RemainingSeconds,
    [property: JsonPropertyName("reason")] string Reason);

internal sealed record AccessRequestRow(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("examCode")] string ExamCode,
    [property: JsonPropertyName("registrationId")] string RegistrationId,
    [property: JsonPropertyName("candidateName")] string CandidateName,
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("reason")] string Reason,
    [property: JsonPropertyName("requestedBy")] string RequestedBy,
    [property: JsonPropertyName("requestedAt")] DateTimeOffset? RequestedAt);

internal sealed record AdminAccountRow(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("email")] string Email,
    [property: JsonPropertyName("adminName")] string AdminName,
    [property: JsonPropertyName("examName")] string? ExamName,
    [property: JsonPropertyName("serviceAvailed")] string? ServiceAvailed,
    [property: JsonPropertyName("role")] string Role,
    [property: JsonPropertyName("active")] bool Active);

internal sealed record DesktopDeviceRow(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("deviceId")] string DeviceId,
    [property: JsonPropertyName("deviceName")] string DeviceName,
    [property: JsonPropertyName("active")] bool Active,
    [property: JsonPropertyName("createdAt")] DateTimeOffset? CreatedAt,
    [property: JsonPropertyName("updatedAt")] DateTimeOffset? UpdatedAt,
    [property: JsonPropertyName("lastSeenAt")] DateTimeOffset? LastSeenAt,
    [property: JsonPropertyName("revokedAt")] DateTimeOffset? RevokedAt);

internal sealed record OpsSessionSnapshot(
    [property: JsonPropertyName("activeCount")] long ActiveCount,
    [property: JsonPropertyName("rowCount")] int RowCount,
    [property: JsonPropertyName("generatedAt")] DateTimeOffset GeneratedAt,
    [property: JsonPropertyName("rows")] List<OpsSessionRow> Rows);

internal sealed record OpsSessionRow(
    [property: JsonPropertyName("attemptSessionId")] long AttemptSessionId,
    [property: JsonPropertyName("examCode")] string ExamCode,
    [property: JsonPropertyName("registrationId")] string RegistrationId,
    [property: JsonPropertyName("candidateName")] string CandidateName,
    [property: JsonPropertyName("lastHeartbeatAt")] DateTimeOffset? LastHeartbeatAt,
    [property: JsonPropertyName("remainingSeconds")] long RemainingSeconds,
    [property: JsonPropertyName("active")] bool Active,
    [property: JsonPropertyName("interruptionReason")] string? InterruptionReason);

internal sealed record LobbySnapshot(
    bool Authenticated,
    bool MasterAccess,
    int ExamCount,
    long CandidateCount,
    long ResponseCount,
    int IncidentCount,
    long ActiveSessionCount,
    int PendingRequestCount,
    int AdminCount,
    List<ExamRow> Exams,
    List<IncidentRow> Incidents,
    List<AccessRequestRow> Requests,
    List<OpsSessionRow> ActiveSessions,
    List<AdminAccountRow> Admins,
    List<DesktopDeviceRow> DesktopDevices,
    string Status);

internal sealed class OpsApiException : Exception
{
    internal OpsApiException(string message, int statusCode) : base(message)
    {
        StatusCode = statusCode;
    }

    internal int StatusCode { get; }
}
