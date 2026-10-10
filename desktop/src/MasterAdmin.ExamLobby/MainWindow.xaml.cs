using System.IO;
using System.Windows;
using System.Windows.Input;
using Microsoft.Web.WebView2.Core;

namespace MasterAdmin.ExamLobby;

public partial class MainWindow : Window
{
    private readonly OpsApiClient _api = new();
    private bool _unlocked;
    private bool _browserInitialized;

    public MainWindow()
    {
        InitializeComponent();
    }

    private async void UnlockClicked(object sender, RoutedEventArgs e)
    {
        GateErrorPanel.Visibility = Visibility.Collapsed;
        if (string.IsNullOrWhiteSpace(EmailBox.Text) || string.IsNullOrWhiteSpace(PasswordBox.Password))
        {
            ShowGateError("Enter authorized email and password.");
            DesktopDialogService.Warning(this, "Access Required", "Enter authorized email and password.");
            return;
        }

        UnlockButton.IsEnabled = false;
        HeaderStatusText.Text = "VERIFYING";
        try
        {
            await _api.SignInAsync(EmailBox.Text, PasswordBox.Password);
            _unlocked = true;
            GatePanel.Visibility = Visibility.Collapsed;
            DashboardPanel.Visibility = Visibility.Visible;
            HeaderStatusText.Text = "UNLOCKED";
            await RefreshSnapshotAsync();
        }
        catch (Exception ex)
        {
            HeaderStatusText.Text = "LOCKED";
            string message = DesktopDialogService.Clean(ex.Message);
            ShowGateError(message);
            DesktopDialogService.Warning(this, "Access Not Granted", message);
        }
        finally
        {
            UnlockButton.IsEnabled = true;
        }
    }

    private async void RefreshClicked(object sender, RoutedEventArgs e)
    {
        await RefreshSnapshotAsync();
    }

    private void LockClicked(object sender, RoutedEventArgs e)
    {
        _unlocked = false;
        BrowserPanel.Visibility = Visibility.Collapsed;
        DashboardPanel.Visibility = Visibility.Collapsed;
        GatePanel.Visibility = Visibility.Visible;
        HeaderStatusText.Text = "LOCKED";
        PasswordBox.Clear();
        ClearDashboard();
    }

    private void ClearGateClicked(object sender, RoutedEventArgs e)
    {
        EmailBox.Clear();
        PasswordBox.Clear();
        GateErrorPanel.Visibility = Visibility.Collapsed;
    }

    private void OpenAdminConsoleClicked(object sender, RoutedEventArgs e)
    {
        OpenLocalConsole("/console/8f3a91d6c42e7b5a0d19/admin");
    }

    private void OpenMasterConsoleClicked(object sender, RoutedEventArgs e)
    {
        OpenLocalConsole("/console/c7e42a9d51f8603b7a24/master");
    }

    private void OpenLocalConsole(string path)
    {
        if (!_unlocked)
        {
            ShowGateError("Unlock the desktop lobby first.");
            DesktopDialogService.Warning(this, "Lobby Locked", "Unlock the desktop lobby first.");
            return;
        }
        string url = "http://localhost:8081" + path;
        _ = OpenProtectedBrowserAsync(path, url);
    }

    private async Task OpenProtectedBrowserAsync(string path, string url)
    {
        DashboardPanel.Visibility = Visibility.Collapsed;
        BrowserPanel.Visibility = Visibility.Visible;
        BrowserTitleText.Text = path.Contains("master", StringComparison.OrdinalIgnoreCase)
            ? "Protected Master Console"
            : "Protected Exam Admin Console";
        await InitializeProtectedBrowserAsync();
        ProtectedBrowser.Source = new Uri(url);
        BottomStatusText.Text = "Protected console opened.";
    }

    private async Task InitializeProtectedBrowserAsync()
    {
        if (_browserInitialized) return;
        _browserInitialized = true;
        await DesktopConsoleSigner.EnsureEnrolledAsync(_api.Http);
        string dataFolder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "MasterAdminExamLobby",
            "WebView2");
        CoreWebView2Environment environment = await CoreWebView2Environment.CreateAsync(userDataFolder: dataFolder);
        await ProtectedBrowser.EnsureCoreWebView2Async(environment);
        ProtectedBrowser.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
        ProtectedBrowser.CoreWebView2.Settings.AreBrowserAcceleratorKeysEnabled = false;
        ProtectedBrowser.CoreWebView2.Settings.AreDefaultScriptDialogsEnabled = false;
        ProtectedBrowser.CoreWebView2.Settings.AreDevToolsEnabled = false;
        ProtectedBrowser.CoreWebView2.AddWebResourceRequestedFilter("http://localhost:8081/*", CoreWebView2WebResourceContext.All);
        ProtectedBrowser.CoreWebView2.AddWebResourceRequestedFilter("http://127.0.0.1:8081/*", CoreWebView2WebResourceContext.All);
        ProtectedBrowser.CoreWebView2.WebResourceRequested += (_, args) =>
        {
            try
            {
                Uri uri = new(args.Request.Uri);
                IReadOnlyDictionary<string, string> headers = DesktopConsoleSigner.Headers(args.Request.Method, uri.AbsolutePath);
                foreach (KeyValuePair<string, string> header in headers)
                {
                    args.Request.Headers.SetHeader(header.Key, header.Value);
                }
            }
            catch
            {
                // Backend will reject sensitive routes that are not signed.
            }
        };
        ProtectedBrowser.CoreWebView2.NavigationStarting += (_, _) => BrowserAddressText.Text = "Loading protected console...";
        ProtectedBrowser.CoreWebView2.NavigationCompleted += async (_, args) =>
        {
            BrowserAddressText.Text = args.IsSuccess ? "Protected console ready." : "Protected console navigation failed.";
            if (!args.IsSuccess) return;
            await RestoreBrowserInputReadinessAsync();
        };
        ProtectedBrowser.CoreWebView2.ScriptDialogOpening += (_, args) =>
            DesktopDialogService.HandleScriptDialog(this, args, "Protected Console");
        ProtectedBrowser.CoreWebView2.NewWindowRequested += (_, args) =>
        {
            args.Handled = true;
            if (!string.IsNullOrWhiteSpace(args.Uri))
            {
                ProtectedBrowser.CoreWebView2.Navigate(args.Uri);
            }
        };
    }

    private void CloseBrowserClicked(object sender, RoutedEventArgs e)
    {
        BrowserPanel.Visibility = Visibility.Collapsed;
        DashboardPanel.Visibility = Visibility.Visible;
    }

    private void ReloadBrowserClicked(object sender, RoutedEventArgs e)
    {
        ProtectedBrowser.Reload();
    }

    private async Task RefreshSnapshotAsync()
    {
        if (!_unlocked) return;
        RefreshButton.IsEnabled = false;
        SnapshotStatusText.Text = "Refreshing...";
        BottomStatusText.Text = "Reading secure operation state.";
        try
        {
            LobbySnapshot snapshot = await _api.SnapshotAsync();
            ExamCountText.Text = snapshot.ExamCount.ToString();
            CandidateCountText.Text = snapshot.CandidateCount.ToString();
            ResponseCountText.Text = snapshot.ResponseCount.ToString();
            IncidentCountText.Text = snapshot.IncidentCount.ToString();
            ActiveSessionCountText.Text = snapshot.ActiveSessionCount.ToString();
            PendingCountText.Text = snapshot.PendingRequestCount.ToString();
            AdminCountText.Text = snapshot.MasterAccess ? snapshot.AdminCount.ToString() : "ADMIN";
            ExamsGrid.ItemsSource = snapshot.Exams;
            IncidentsGrid.ItemsSource = snapshot.Incidents;
            RequestsGrid.ItemsSource = snapshot.Requests;
            ActiveSessionsGrid.ItemsSource = snapshot.ActiveSessions;
            AdminsGrid.ItemsSource = snapshot.Admins;
            DesktopDevicesGrid.ItemsSource = snapshot.DesktopDevices;
            SnapshotStatusText.Text = snapshot.Status;
            BottomStatusText.Text = snapshot.MasterAccess
                ? "Master authority verified. Admin registry visible."
                : "Exam-admin/moderator access verified. Master registry hidden.";
        }
        catch (Exception ex)
        {
            SnapshotStatusText.Text = "Refresh failed.";
            string message = DesktopDialogService.Clean(ex.Message);
            BottomStatusText.Text = "Secure service communication failed.";
            DesktopDialogService.Warning(this, "Communication Error", message);
        }
        finally
        {
            RefreshButton.IsEnabled = true;
        }
    }

    private void ClearDashboard()
    {
        ExamCountText.Text = "0";
        CandidateCountText.Text = "0";
        ResponseCountText.Text = "0";
        IncidentCountText.Text = "0";
        ActiveSessionCountText.Text = "0";
        PendingCountText.Text = "0";
        AdminCountText.Text = "-";
        ExamsGrid.ItemsSource = null;
        IncidentsGrid.ItemsSource = null;
        RequestsGrid.ItemsSource = null;
        ActiveSessionsGrid.ItemsSource = null;
        AdminsGrid.ItemsSource = null;
        DesktopDevicesGrid.ItemsSource = null;
        SnapshotStatusText.Text = "Waiting for refresh.";
        BottomStatusText.Text = "Master lobby separated from candidate exam client.";
    }

    private void ShowGateError(string message)
    {
        GateErrorText.Text = message;
        GateErrorPanel.Visibility = Visibility.Visible;
    }

    private async Task RestoreBrowserInputReadinessAsync()
    {
        if (ProtectedBrowser.CoreWebView2 is null) return;
        const string script = """
            (() => {
              const body = document.body;
              if (body) {
                body.style.caretColor = '';
              }
              const active = document.activeElement;
              const tag = active && active.tagName ? active.tagName.toLowerCase() : '';
              const editable = !!(active && (
                active.isContentEditable ||
                tag === 'input' ||
                tag === 'textarea' ||
                tag === 'select'
              ));
              if (editable) return true;
              const selector = 'input:not([type="hidden"]):not([disabled]):not([readonly]), textarea:not([disabled]):not([readonly]), select:not([disabled]), [contenteditable=""], [contenteditable="true"]';
              const first = document.querySelector(selector);
              if (!first) return false;
              try {
                first.focus({ preventScroll: true });
                return true;
              } catch {
                return false;
              }
            })();
            """;
        try
        {
            await ProtectedBrowser.CoreWebView2.ExecuteScriptAsync(script);
        }
        catch
        {
            // Navigation can race focus restore; safe to ignore.
        }
    }

}
