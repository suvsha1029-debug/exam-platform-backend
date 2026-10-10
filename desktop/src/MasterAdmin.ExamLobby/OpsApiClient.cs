using System.Net;
using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json;

namespace MasterAdmin.ExamLobby;

internal sealed class OpsApiClient
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    private readonly HttpClient _http;
    internal HttpClient Http => _http;

    internal OpsApiClient()
    {
        HttpClientHandler handler = new()
        {
            AllowAutoRedirect = false,
            UseCookies = true,
            CookieContainer = new CookieContainer()
        };
        _http = new HttpClient(handler)
        {
            BaseAddress = new Uri("http://localhost:8081/"),
            Timeout = TimeSpan.FromSeconds(20)
        };
    }

    internal async Task SignInAsync(string email, string password)
    {
        using FormUrlEncodedContent form = new(new Dictionary<string, string>
        {
            ["username"] = email.Trim(),
            ["password"] = password
        });
        using HttpResponseMessage response = await _http.PostAsync("/login", form);
        if (response.StatusCode is HttpStatusCode.Found or HttpStatusCode.SeeOther or HttpStatusCode.Redirect)
        {
            string location = response.Headers.Location?.ToString() ?? "";
            if (!location.Contains("error", StringComparison.OrdinalIgnoreCase))
            {
                return;
            }
        }
        string body = await response.Content.ReadAsStringAsync();
        throw new OpsApiException(FriendlyError(body, "Unable to sign in. Check admin email/password."), (int)response.StatusCode);
    }

    internal async Task<LobbySnapshot> SnapshotAsync()
    {
        List<ExamRow> exams = await GetAsync<List<ExamRow>>("/api/exams");
        PageResponse<StudentRow> students = await GetAsync<PageResponse<StudentRow>>("/api/students?page=0&size=1");
        PageResponse<ResponseRow> responses = await GetAsync<PageResponse<ResponseRow>>("/api/responses?page=0&size=1");
        List<IncidentRow> incidents = await GetAsync<List<IncidentRow>>("/api/exam-access/incidents");
        List<AccessRequestRow> requests = await GetAsync<List<AccessRequestRow>>("/api/exam-access/requests");
        OpsSessionSnapshot sessionSnapshot = await GetAsync<OpsSessionSnapshot>("/api/admin/monitoring/sessions");

        bool masterAccess = true;
        List<AdminAccountRow> admins = [];
        List<DesktopDeviceRow> desktopDevices = [];
        try
        {
            admins = await GetAsync<List<AdminAccountRow>>("/api/master-admin/accounts");
            desktopDevices = await GetAsync<List<DesktopDeviceRow>>("/api/master-admin/desktop-devices");
        }
        catch (OpsApiException ex) when (ex.StatusCode is 401 or 403)
        {
            masterAccess = false;
        }

        return new LobbySnapshot(
            Authenticated: true,
            MasterAccess: masterAccess,
            ExamCount: exams.Count,
            CandidateCount: students.TotalElements,
            ResponseCount: responses.TotalElements,
            IncidentCount: incidents.Count,
            ActiveSessionCount: sessionSnapshot.ActiveCount,
            PendingRequestCount: requests.Count(item => string.Equals(item.Status, "PENDING", StringComparison.OrdinalIgnoreCase)),
            AdminCount: admins.Count,
            Exams: exams,
            Incidents: incidents,
            Requests: requests,
            ActiveSessions: sessionSnapshot.Rows,
            Admins: admins,
            DesktopDevices: desktopDevices,
            Status: "Secure service connected at " + DateTimeOffset.Now.ToString("yyyy-MM-dd HH:mm:ss zzz"));
    }

    private async Task<T> GetAsync<T>(string path)
    {
        using HttpRequestMessage request = new(HttpMethod.Get, path);
        await DesktopConsoleSigner.ApplyAsync(request, _http);
        using HttpResponseMessage response = await _http.SendAsync(request);
        string body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
        {
            throw new OpsApiException(FriendlyError(body, "Request failed."), (int)response.StatusCode);
        }
        T? parsed = JsonSerializer.Deserialize<T>(body, JsonOptions);
        return parsed ?? throw new OpsApiException("Server returned an empty response.", (int)response.StatusCode);
    }

    private static string FriendlyError(string body, string fallback)
    {
        if (string.IsNullOrWhiteSpace(body)) return fallback;
        try
        {
            using JsonDocument document = JsonDocument.Parse(body);
            JsonElement root = document.RootElement;
            if (root.TryGetProperty("detail", out JsonElement detail) && detail.ValueKind == JsonValueKind.String)
            {
                return detail.GetString() ?? fallback;
            }
            if (root.TryGetProperty("message", out JsonElement message) && message.ValueKind == JsonValueKind.String)
            {
                return message.GetString() ?? fallback;
            }
        }
        catch (JsonException)
        {
            // HTML login errors and plain text responses land here.
        }
        return body.Trim().Length > 220 ? body.Trim()[..220] : body.Trim();
    }
}
