using System.Diagnostics;
using System.IO;
using System.Text.Json;

namespace QSStudio.Desktop;

internal sealed record SecureClientConfig(
    string BackendUrl,
    string InstallMode,
    bool AutoUninstallAfterSubmit,
    string UninstallScriptPath)
{
    internal static SecureClientConfig Load()
    {
        string exeDir = AppContext.BaseDirectory;
        string[] candidates =
        [
            Path.Combine(exeDir, "secure-client-config.json"),
            Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "QSStudio",
                "SecureExamClient",
                "secure-client-config.json")
        ];
        foreach (string path in candidates)
        {
            SecureClientConfig? config = ReadConfig(path);
            if (config != null)
            {
                return config.Normalize();
            }
        }
        return new SecureClientConfig("http://localhost:8081/", "dev", false, "").Normalize();
    }

    internal Uri BackendUri()
    {
        string value = string.IsNullOrWhiteSpace(BackendUrl) ? "http://localhost:8081/" : BackendUrl.Trim();
        if (!value.EndsWith("/", StringComparison.Ordinal)) value += "/";
        return Uri.TryCreate(value, UriKind.Absolute, out Uri? uri)
            ? uri
            : new Uri("http://localhost:8081/");
    }

    internal bool ShouldAutoUninstall => AutoUninstallAfterSubmit
        && !string.Equals(InstallMode, "dev", StringComparison.OrdinalIgnoreCase)
        && !string.IsNullOrWhiteSpace(UninstallScriptPath)
        && File.Exists(UninstallScriptPath);

    internal void StartPostSubmitCleanup()
    {
        if (!ShouldAutoUninstall) return;
        try
        {
            string currentPid = Environment.ProcessId.ToString(System.Globalization.CultureInfo.InvariantCulture);
            string args = "-NoProfile -ExecutionPolicy Bypass -File "
                + Quote(UninstallScriptPath)
                + " -Silent -DelayPid "
                + currentPid;
            Process.Start(new ProcessStartInfo
            {
                FileName = "powershell.exe",
                Arguments = args,
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden
            });
        }
        catch
        {
            // Local cleanup is best-effort. Server-side submission is already complete.
        }
    }

    private SecureClientConfig Normalize()
    {
        return this with
        {
            BackendUrl = BackendUri().ToString(),
            InstallMode = string.IsNullOrWhiteSpace(InstallMode) ? "dev" : InstallMode.Trim(),
            UninstallScriptPath = UninstallScriptPath?.Trim() ?? ""
        };
    }

    private static SecureClientConfig? ReadConfig(string path)
    {
        try
        {
            if (!File.Exists(path)) return null;
            string json = File.ReadAllText(path);
            return JsonSerializer.Deserialize<SecureClientConfig>(json, JsonDefaults.Options);
        }
        catch
        {
            return null;
        }
    }

    private static string Quote(string value)
    {
        return "\"" + value.Replace("\"", "\\\"", StringComparison.Ordinal) + "\"";
    }
}
