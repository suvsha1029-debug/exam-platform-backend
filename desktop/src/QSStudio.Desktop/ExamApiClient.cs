using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json;

namespace QSStudio.Desktop;

internal sealed class ExamApiClient
{
    private readonly HttpClient _http;
    private string _candidateSecurityToken = "";

    internal ExamApiClient(Uri baseAddress)
    {
        HttpClientHandler handler = new()
        {
            UseCookies = true,
            CookieContainer = new System.Net.CookieContainer()
        };
        _http = new HttpClient(handler)
        {
            BaseAddress = baseAddress,
            Timeout = TimeSpan.FromSeconds(30)
        };
    }

    internal Task<NativeExamSessionResponse> StartNativeSessionAsync(string registrationId, string dateOfBirth)
    {
        return CandidatePostAsync<object, NativeExamSessionResponse>("/api/candidate/native-exam/session", new
        {
            registrationId,
            dateOfBirth
        });
    }

    internal async Task<List<PublicExamResponse>> PublicExamsAsync()
    {
        using HttpResponseMessage response = await _http.GetAsync("/api/public/exams");
        string body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
        {
            throw new ApiException(FriendlyError(body), (int)response.StatusCode);
        }
        return JsonSerializer.Deserialize<List<PublicExamResponse>>(body, JsonDefaults.Options) ?? [];
    }

    internal Task<BootstrapResponse> BootstrapAsync(string token)
    {
        return CandidatePostAsync<object, BootstrapResponse>("/api/candidate/server-exam/bootstrap", new
        {
            examSessionToken = token
        });
    }

    internal Task<PaperResponse> PaperAsync(string token)
    {
        return CandidatePostAsync<object, PaperResponse>("/api/candidate/server-exam/paper", new
        {
            examSessionToken = token
        });
    }

    internal Task<QuestionResponse> QuestionAsync(string token, int displayIndex)
    {
        return CandidatePostAsync<object, QuestionResponse>("/api/candidate/server-exam/question", new
        {
            examSessionToken = token,
            displayIndex
        });
    }

    internal Task<AnswerResponse> AnswerAsync(
        string token,
        int displayIndex,
        IReadOnlyCollection<string> markedOptionIds,
        string natAnswer,
        string status,
        bool markedForReview,
        long timeSpentMs)
    {
        return CandidatePostAsync<object, AnswerResponse>("/api/candidate/server-exam/answer", new
        {
            examSessionToken = token,
            displayIndex,
            markedOptionIds,
            natAnswer,
            status,
            markedForReview,
            timeSpentMs,
            activity = new { client = "native-desktop", savedAt = DateTimeOffset.UtcNow }
        });
    }

    internal Task<ExamSubmitResponse> SubmitAsync(string token)
    {
        return CandidatePostAsync<object, ExamSubmitResponse>("/api/candidate/server-exam/submit", new
        {
            examSessionToken = token
        });
    }

    internal Task HeartbeatAsync(string token)
    {
        return CandidatePostNoResponseAsync("/api/candidate/exam-heartbeat", new
        {
            examSessionToken = token
        });
    }

    internal Task FlagInterruptionAsync(string token, string reason = "")
    {
        return CandidatePostNoResponseAsync("/api/candidate/exam-interruption", new
        {
            examSessionToken = token,
            reason
        });
    }

    private async Task EnsureCandidateSecurityTokenAsync()
    {
        if (!string.IsNullOrWhiteSpace(_candidateSecurityToken)) return;
        using HttpResponseMessage response = await _http.GetAsync("/api/public/candidate-security-token");
        string body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
        {
            throw new ApiException(FriendlyError(body), (int)response.StatusCode);
        }
        CandidateSecurityTokenResponse? token = JsonSerializer.Deserialize<CandidateSecurityTokenResponse>(body, JsonDefaults.Options);
        _candidateSecurityToken = token?.Token ?? "";
        if (string.IsNullOrWhiteSpace(_candidateSecurityToken))
        {
            throw new ApiException("Could not initialize candidate security token.", (int)response.StatusCode);
        }
    }

    private async Task<TResponse> CandidatePostAsync<TRequest, TResponse>(string path, TRequest request)
    {
        await EnsureCandidateSecurityTokenAsync();
        return await PostAsync<TRequest, TResponse>(path, request, candidateSecurity: true);
    }

    private async Task CandidatePostNoResponseAsync<TRequest>(string path, TRequest request)
    {
        await EnsureCandidateSecurityTokenAsync();
        await PostNoResponseAsync(path, request, candidateSecurity: true);
    }

    private async Task<TResponse> PostAsync<TRequest, TResponse>(string path, TRequest request, bool candidateSecurity)
    {
        using HttpRequestMessage message = new(HttpMethod.Post, path)
        {
            Content = JsonContent.Create(request, options: JsonDefaults.Options)
        };
        if (candidateSecurity)
        {
            message.Headers.Add("X-Candidate-CSRF", _candidateSecurityToken);
        }
        using HttpResponseMessage response = await _http.SendAsync(message);
        string body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
        {
            throw new ApiException(FriendlyError(body), (int)response.StatusCode);
        }
        TResponse? parsed = JsonSerializer.Deserialize<TResponse>(body, JsonDefaults.Options);
        return parsed ?? throw new ApiException("Server returned an empty response.", (int)response.StatusCode);
    }

    private async Task PostNoResponseAsync<TRequest>(string path, TRequest request, bool candidateSecurity)
    {
        using HttpRequestMessage message = new(HttpMethod.Post, path)
        {
            Content = JsonContent.Create(request, options: JsonDefaults.Options)
        };
        if (candidateSecurity)
        {
            message.Headers.Add("X-Candidate-CSRF", _candidateSecurityToken);
        }
        using HttpResponseMessage response = await _http.SendAsync(message);
        if (!response.IsSuccessStatusCode)
        {
            string body = await response.Content.ReadAsStringAsync();
            throw new ApiException(FriendlyError(body), (int)response.StatusCode);
        }
    }

    private static string FriendlyError(string body)
    {
        if (string.IsNullOrWhiteSpace(body))
        {
            return "The server rejected this request.";
        }
        try
        {
            using JsonDocument document = JsonDocument.Parse(body);
            JsonElement root = document.RootElement;
            if (root.TryGetProperty("detail", out JsonElement detail))
            {
                string? text = detail.GetString();
                if (!string.IsNullOrWhiteSpace(text)) return text;
            }
            if (root.TryGetProperty("message", out JsonElement message))
            {
                string? text = message.GetString();
                if (!string.IsNullOrWhiteSpace(text)) return text;
            }
            if (root.TryGetProperty("title", out JsonElement title))
            {
                string? text = title.GetString();
                if (!string.IsNullOrWhiteSpace(text)) return text;
            }
        }
        catch (JsonException)
        {
            // Fall through to trimmed body.
        }
        return body.Trim();
    }
}
