using System.IO;
using System.Net.Http;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace MasterAdmin.ExamLobby;

internal static class DesktopConsoleSigner
{
    private const string EnrollmentPath = "/api/desktop-console/enroll";
    private static readonly SemaphoreSlim Gate = new(1, 1);
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };
    private static DeviceCredential? _credential;
    private static ECDsa? _key;

    internal static async Task EnsureEnrolledAsync(HttpClient http)
    {
        if (_credential != null && _key != null) return;
        await Gate.WaitAsync();
        try
        {
            if (_credential != null && _key != null) return;
            LoadOrCreateCredential();
            await EnrollAsync(http);
        }
        finally
        {
            Gate.Release();
        }
    }

    internal static async Task ApplyAsync(HttpRequestMessage message, HttpClient http)
    {
        await EnsureEnrolledAsync(http);
        Apply(message.Method.Method, PathOnly(message.RequestUri), (name, value) =>
        {
            message.Headers.Remove(name);
            message.Headers.Add(name, value);
        });
    }

    internal static IReadOnlyDictionary<string, string> Headers(string method, string path)
    {
        if (_credential == null || _key == null)
        {
            throw new InvalidOperationException("Desktop console device is not enrolled yet.");
        }
        Dictionary<string, string> headers = new(StringComparer.OrdinalIgnoreCase);
        Apply(method, path, (name, value) => headers[name] = value);
        return headers;
    }

    private static void Apply(string method, string path, Action<string, string> add)
    {
        string timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds().ToString();
        string nonce = Convert.ToHexString(RandomNumberGenerator.GetBytes(16)).ToLowerInvariant();
        string deviceId = _credential?.DeviceId ?? throw new InvalidOperationException("Desktop console device is not enrolled.");
        string canonical = method.ToUpperInvariant() + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + deviceId;
        byte[] signature = _key!.SignData(
            Encoding.UTF8.GetBytes(canonical),
            HashAlgorithmName.SHA256,
            DSASignatureFormat.Rfc3279DerSequence);
        add("X-Desktop-Console-Device", deviceId);
        add("X-Desktop-Console-Timestamp", timestamp);
        add("X-Desktop-Console-Nonce", nonce);
        add("X-Desktop-Console-Signature", Convert.ToBase64String(signature));
    }

    private static async Task EnrollAsync(HttpClient http)
    {
        string token = Environment.GetEnvironmentVariable("DESKTOP_CONSOLE_ENROLLMENT_TOKEN")?.Trim()
            ?? "local-dev-console-enrollment-token-change-me";
        using HttpRequestMessage request = new(HttpMethod.Post, EnrollmentPath);
        request.Headers.Add("X-Desktop-Enrollment-Token", token);
        request.Content = JsonContent.Create(new
        {
            deviceId = _credential!.DeviceId,
            deviceName = _credential.DeviceName,
            publicKeyBase64 = _credential.PublicKeyBase64
        });
        using HttpResponseMessage response = await http.SendAsync(request);
        if (!response.IsSuccessStatusCode)
        {
            string body = await response.Content.ReadAsStringAsync();
            throw new InvalidOperationException("Desktop console device enrollment failed. " + FriendlyError(body));
        }
    }

    private static void LoadOrCreateCredential()
    {
        string file = CredentialFile();
        if (File.Exists(file))
        {
            DeviceCredential? loaded = JsonSerializer.Deserialize<DeviceCredential>(File.ReadAllText(file));
            if (loaded != null && !string.IsNullOrWhiteSpace(loaded.DeviceId) && !string.IsNullOrWhiteSpace(loaded.ProtectedPrivateKeyBase64))
            {
                byte[] protectedKey = Convert.FromBase64String(loaded.ProtectedPrivateKeyBase64);
                byte[] privateKey = ProtectedData.Unprotect(protectedKey, null, DataProtectionScope.CurrentUser);
                ECDsa key = ECDsa.Create();
                key.ImportPkcs8PrivateKey(privateKey, out _);
                CryptographicOperations.ZeroMemory(privateKey);
                _credential = loaded;
                _key = key;
                return;
            }
        }
        Directory.CreateDirectory(Path.GetDirectoryName(file)!);
        using ECDsa generated = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        byte[] privateKeyBytes = generated.ExportPkcs8PrivateKey();
        byte[] protectedPrivateKey = ProtectedData.Protect(privateKeyBytes, null, DataProtectionScope.CurrentUser);
        CryptographicOperations.ZeroMemory(privateKeyBytes);
        DeviceCredential credential = new(
            DeviceId: "desktop-" + Convert.ToHexString(RandomNumberGenerator.GetBytes(16)).ToLowerInvariant(),
            DeviceName: Environment.MachineName + "\\" + Environment.UserName,
            PublicKeyBase64: Convert.ToBase64String(generated.ExportSubjectPublicKeyInfo()),
            ProtectedPrivateKeyBase64: Convert.ToBase64String(protectedPrivateKey),
            CreatedAtUtc: DateTimeOffset.UtcNow);
        File.WriteAllText(file, JsonSerializer.Serialize(credential, JsonOptions));
        byte[] generatedPrivateKey = ProtectedData.Unprotect(protectedPrivateKey, null, DataProtectionScope.CurrentUser);
        ECDsa generatedKey = ECDsa.Create();
        generatedKey.ImportPkcs8PrivateKey(generatedPrivateKey, out _);
        CryptographicOperations.ZeroMemory(generatedPrivateKey);
        _credential = credential;
        _key = generatedKey;
    }

    private static string CredentialFile()
    {
        return Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "MasterAdminExamLobby",
            "desktop-console-device.json");
    }

    private static string PathOnly(Uri? uri)
    {
        string path = uri?.PathAndQuery ?? "/";
        int query = path.IndexOf('?');
        return query >= 0 ? path[..query] : path;
    }

    private static string FriendlyError(string body)
    {
        if (string.IsNullOrWhiteSpace(body)) return "";
        try
        {
            using JsonDocument document = JsonDocument.Parse(body);
            if (document.RootElement.TryGetProperty("detail", out JsonElement detail) && detail.ValueKind == JsonValueKind.String)
            {
                return detail.GetString() ?? "";
            }
        }
        catch (JsonException)
        {
        }
        return body.Trim().Length > 220 ? body.Trim()[..220] : body.Trim();
    }

    private sealed record DeviceCredential(
        string DeviceId,
        string DeviceName,
        string PublicKeyBase64,
        string ProtectedPrivateKeyBase64,
        DateTimeOffset CreatedAtUtc);
}
