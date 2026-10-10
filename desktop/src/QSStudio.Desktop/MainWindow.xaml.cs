using System.ComponentModel;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text.RegularExpressions;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Interop;
using System.Windows.Threading;
using Microsoft.Win32;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;

namespace QSStudio.Desktop;

public partial class MainWindow : Window
{
    private static readonly Regex NatDraftPattern = new(@"^[+-]?((\d+(\.\d*)?)|(\.\d*))?$", RegexOptions.Compiled);
    private readonly SecureClientConfig _clientConfig;
    private readonly ExamApiClient _api;
    private readonly DispatcherTimer _guardTimer;
    private readonly DispatcherTimer _heartbeatTimer;
    private readonly DispatcherTimer _clockTimer;
    private readonly DispatcherTimer _entryDelayTimer;
    private WindowState _restoreState = WindowState.Normal;
    private WindowStyle _restoreStyle = WindowStyle.SingleBorderWindow;
    private ResizeMode _restoreResizeMode = ResizeMode.CanResize;
    private bool _studioInitialized;
    private bool _examActive;
    private bool _submitted;
    private bool _webExamMode;
    private bool _switchingQuestion;
    private bool _startInProgress;
    private bool _submitInProgress;
    private DateTimeOffset _dueAt;
    private DateTimeOffset _questionOpenedAt;
    private NativeExamSessionResponse? _session;
    private BootstrapResponse? _bootstrap;
    private QuestionResponse? _currentQuestion;
    private readonly Dictionary<int, QuestionResponse> _paper = new();
    private bool _paperReloadedForRender;
    private bool _desktopExamBridgePending;
    private string _desktopExamBridgeMessageJson = "";
    private DateTimeOffset _entryAllowedAt;
    private bool _entryDelayComplete;
    private bool _entryDelayEnabled = true;
    private int _entryDelaySeconds = 120;
    private int _integrityViolationCount;
    private bool _candidateBlockedForRejoin;
    private bool _nativeLockProbeActive;
    private bool _powerSuspendSignalSeen;
    private bool _captureProtectionApplied;
    private bool _postSubmitCleanupStarted;
    private DateTimeOffset _lastIntegritySignalAt = DateTimeOffset.MinValue;
    private string _lastIntegritySignalKind = "";
    private readonly HashSet<string> _selectedOptionIds = new(StringComparer.OrdinalIgnoreCase);
    private readonly SemaphoreSlim _answerSaveLock = new(1, 1);
    private const uint WdaNone = 0x00000000;
    private const uint WdaMonitor = 0x00000001;
    private const uint WdaExcludeFromCapture = 0x00000011;

    public MainWindow()
    {
        _clientConfig = SecureClientConfig.Load();
        _api = new ExamApiClient(_clientConfig.BackendUri());
        InitializeComponent();
        Loaded += MainWindowLoaded;
        Closing += MainWindowClosing;
        KeyDown += MainWindowKeyDown;
        PreviewKeyDown += MainWindowPreviewKeyDown;
        PreviewMouseWheel += MainWindowPreviewMouseWheel;
        SystemEvents.SessionSwitch += SystemEventsSessionSwitch;
        SystemEvents.PowerModeChanged += SystemEventsPowerModeChanged;

        _guardTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _guardTimer.Tick += (_, _) => GuardTick();
        _heartbeatTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(15) };
        _heartbeatTimer.Tick += async (_, _) => await HeartbeatAsync();
        _clockTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _clockTimer.Tick += async (_, _) => await ClockTickAsync();
        _entryDelayTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _entryDelayTimer.Tick += (_, _) => UpdateEntryDelay();
    }

    private void MainWindowLoaded(object sender, RoutedEventArgs e)
    {
        RenderGuardStatus();
        StartEntryDelay();
        _ = WarmUpNativeEntryAsync();
        ShowNativeExam();
    }

    private async Task InitializeBrowserAsync(bool navigateHome = true)
    {
        if (_studioInitialized) return;
        _studioInitialized = true;
        StatusText.Text = "Starting protected viewer...";
        string dataFolder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "QSStudio",
            "WebView2");
        CoreWebView2Environment environment = await CoreWebView2Environment.CreateAsync(userDataFolder: dataFolder);
        await StudioView.EnsureCoreWebView2Async(environment);

        StudioView.CoreWebView2.Settings.AreBrowserAcceleratorKeysEnabled = false;
        StudioView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
        StudioView.CoreWebView2.Settings.AreDefaultScriptDialogsEnabled = false;
        StudioView.CoreWebView2.Settings.AreDevToolsEnabled = false;
        StudioView.CoreWebView2.Settings.IsZoomControlEnabled = false;
        StudioView.CoreWebView2.Settings.IsPinchZoomEnabled = false;
        StudioView.ZoomFactor = 1.0;
        StudioView.ZoomFactorChanged += (_, _) => ResetWebViewZoom(StudioView);
        await StudioView.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync(SecureExamZoomLockScript);
        StudioView.CoreWebView2.NavigationStarting += (_, args) =>
        {
            StatusText.Text = "Loading protected page...";
            AddressText.Text = "Secure exam workspace";
            ResetWebViewZoom(StudioView);
        };
        StudioView.CoreWebView2.NavigationCompleted += (_, args) =>
        {
            StatusText.Text = args.IsSuccess ? "Protected page ready" : "Protected page could not load";
            ResetWebViewZoom(StudioView);
        };
        StudioView.CoreWebView2.ScriptDialogOpening += (_, args) =>
            DesktopDialogService.HandleScriptDialog(this, args, "Secure Exam Client");
        StudioView.CoreWebView2.NewWindowRequested += (_, args) =>
        {
            args.Handled = true;
            if (!string.IsNullOrWhiteSpace(args.Uri))
            {
                StudioView.CoreWebView2.Navigate(args.Uri);
            }
        };
        StudioView.CoreWebView2.NavigationCompleted += DesktopExamNavigationCompleted;
        StudioView.CoreWebView2.WebMessageReceived += WebViewMessageReceived;
        if (navigateHome) StudioView.Source = _clientConfig.BackendUri();
    }

    private void DesktopExamNavigationCompleted(object? sender, CoreWebView2NavigationCompletedEventArgs args)
    {
        if (!_desktopExamBridgePending || !args.IsSuccess) return;
        string current = StudioView.Source?.ToString() ?? "";
        if (!current.Contains("candidate-exam.html", StringComparison.OrdinalIgnoreCase)) return;
        _desktopExamBridgePending = false;
        try
        {
            StudioView.CoreWebView2.PostWebMessageAsJson(_desktopExamBridgeMessageJson);
            StatusText.Text = "Exam viewer opened.";
        }
        catch (Exception ex)
        {
            StatusText.Text = "Exam viewer launch failed.";
            string message = DesktopDialogService.Clean(ex.Message);
            NativeLoginMessage.Text = "Exam viewer launch failed.";
            DesktopDialogService.Warning(this, "Exam Viewer", message);
            NativeExamRoot.Visibility = Visibility.Visible;
            StudioView.Visibility = Visibility.Collapsed;
            _examActive = false;
            ExitExamGuard();
        }
    }

    private void RenderGuardStatus()
    {
        GuardSnapshot guard = NativeGuard.Probe();
        GuardStatusText.Text = guard.Loaded ? "Ready" : "Preparing";
        NativeGuardText.Text = _captureProtectionApplied ? "SECURE MODE - CAPTURE BLOCKED" : "SECURE MODE";
    }

    private void StartEntryDelay()
    {
        if (!_entryDelayEnabled || _entryDelaySeconds <= 0)
        {
            _entryDelayTimer.Stop();
            _entryDelayComplete = true;
            NativeStartButton.Content = "Start Exam";
            NativeStartButton.IsEnabled = true;
            NativeLoginMessage.Text = "Entry is available. Enter details to start.";
            return;
        }
        _entryDelayComplete = false;
        _entryAllowedAt = DateTimeOffset.UtcNow.AddSeconds(_entryDelaySeconds);
        NativeStartButton.IsEnabled = false;
        UpdateEntryDelay();
        _entryDelayTimer.Start();
    }

    private void UpdateEntryDelay()
    {
        TimeSpan left = _entryAllowedAt - DateTimeOffset.UtcNow;
        if (left <= TimeSpan.Zero)
        {
            _entryDelayTimer.Stop();
            _entryDelayComplete = true;
            NativeStartButton.Content = "Start Exam";
            NativeStartButton.IsEnabled = true;
            NativeLoginMessage.Text = "Entry is available. Enter details to start.";
            return;
        }

        string countdown = left.ToString(@"mm\:ss", CultureInfo.InvariantCulture);
        NativeStartButton.Content = $"Entry opens in {countdown}";
        NativeStartButton.IsEnabled = false;
        NativeLoginMessage.Text = $"Entry opens in {countdown}.";
    }

    private async Task WarmUpNativeEntryAsync()
    {
        try
        {
            List<PublicExamResponse> exams = await _api.PublicExamsAsync();
            PublicExamResponse? exam = exams.FirstOrDefault(item => item.Active) ?? exams.FirstOrDefault();
            if (exam != null)
            {
                ApplyNativePublicBranding(exam);
            }
        }
        catch
        {
            // Keep the entry screen quiet; verified candidate branding still applies after login.
        }
    }

    private void ReloadClicked(object sender, RoutedEventArgs e)
    {
        if (_submitted)
        {
            StatusText.Text = "Exam is already submitted.";
            return;
        }
        if (StudioView.Visibility == Visibility.Visible) StudioView.Reload();
    }

    private void HomeClicked(object sender, RoutedEventArgs e)
    {
        if (_submitted)
        {
            StatusText.Text = "Exam is already submitted.";
            return;
        }
        if (StudioView.Visibility == Visibility.Visible) StudioView.Source = _clientConfig.BackendUri();
    }

    private void FullScreenClicked(object sender, RoutedEventArgs e) => ToggleFullScreen();

    private async void StudioBrowserClicked(object sender, RoutedEventArgs e)
    {
        if (_examActive)
        {
            StatusText.Text = "Studio browser is locked during an active exam.";
            return;
        }
        NativeExamRoot.Visibility = Visibility.Collapsed;
        StudioView.Visibility = Visibility.Visible;
        await InitializeBrowserAsync();
    }

    private void NativeExamClicked(object sender, RoutedEventArgs e) => ShowNativeExam();

    private void ShowNativeExam()
    {
        if (_examActive) return;
        StudioView.Visibility = Visibility.Collapsed;
        NativeExamRoot.Visibility = Visibility.Visible;
        TopShellBar.Visibility = Visibility.Collapsed;
        BottomStatusBar.Visibility = Visibility.Collapsed;
        EnterFullScreen(topmost: false);
        AddressText.Text = "Candidate exam mode";
        StatusText.Text = "Server: " + _clientConfig.BackendUri().Host;
    }

    private async void WebViewMessageReceived(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        try
        {
            JsonNode? node = JsonNode.Parse(e.WebMessageAsJson);
            string type = node?["type"]?.GetValue<string>() ?? "";
            if (string.Equals(type, "candidate-submit-freeze-started", StringComparison.OrdinalIgnoreCase))
            {
                EnsureExamFullScreen();
                StatusText.Text = "Final submit lock active.";
                return;
            }
            if (!string.Equals(type, "candidate-exam-submitted", StringComparison.OrdinalIgnoreCase)) return;
            _submitted = true;
            _examActive = false;
            _guardTimer.Stop();
            _heartbeatTimer.Stop();
            _clockTimer.Stop();
            ExitExamGuard();
            await ClearWebViewExamDataAsync();
            ShowCompletionScreen("Exam Submitted Successfully", "Your exam was submitted successfully. You may now leave the exam window.", DateTimeOffset.Now);
            StatusText.Text = "Exam submitted.";
        }
        catch
        {
            // Ignore malformed page messages; the guarded shell stays locked.
        }
    }

    private void MainWindowKeyDown(object sender, KeyEventArgs e)
    {
        if (_submitted && IsGuardedExamKey(e))
        {
            SuppressPostSubmitKey(e);
            return;
        }
        if (_examActive)
        {
            if (IsGuardedExamKey(e))
            {
                if ((e.Key == Key.PrintScreen || e.SystemKey == Key.PrintScreen))
                {
                    ClearClipboardQuietly();
                }
                EnsureExamFullScreen();
                e.Handled = true;
            }
            return;
        }

        if (NativeExamRoot.Visibility == Visibility.Visible && (e.Key == Key.F11 || e.Key == Key.Escape))
        {
            EnterFullScreen(topmost: false);
            e.Handled = true;
            return;
        }

        if (e.Key == Key.F11)
        {
            ToggleFullScreen();
            e.Handled = true;
        }
        if (e.Key == Key.Escape && WindowStyle == WindowStyle.None)
        {
            ToggleFullScreen();
            e.Handled = true;
        }
    }

    private void MainWindowPreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (_submitted && (IsGuardedExamKey(e) || IsZoomKey(e)))
        {
            SuppressPostSubmitKey(e);
            return;
        }
        if (!IsZoomKey(e)) return;
        ResetWebViewZoom(StudioView);
        e.Handled = true;
    }

    private void MainWindowPreviewMouseWheel(object sender, MouseWheelEventArgs e)
    {
        if (!Keyboard.Modifiers.HasFlag(ModifierKeys.Control)) return;
        ResetWebViewZoom(StudioView);
        e.Handled = true;
    }

    private static bool IsGuardedExamKey(KeyEventArgs e)
    {
        Key key = e.Key == Key.System ? e.SystemKey : e.Key;
        bool alt = Keyboard.Modifiers.HasFlag(ModifierKeys.Alt);
        bool ctrl = Keyboard.Modifiers.HasFlag(ModifierKeys.Control);
        bool win = Keyboard.Modifiers.HasFlag(ModifierKeys.Windows);
        if (win || alt) return true;
        if (key is Key.Escape or Key.F1 or Key.F2 or Key.F3 or Key.F4 or Key.F5 or Key.F6
            or Key.F7 or Key.F8 or Key.F9 or Key.F10 or Key.F11 or Key.F12
            or Key.PrintScreen or Key.LWin or Key.RWin or Key.Apps) return true;
        if (ctrl && key is Key.R or Key.U or Key.S or Key.P or Key.C or Key.X or Key.A
            or Key.OemPlus or Key.OemMinus or Key.Add or Key.Subtract) return true;
        return false;
    }

    private static bool IsZoomKey(KeyEventArgs e)
    {
        if (!Keyboard.Modifiers.HasFlag(ModifierKeys.Control)) return false;
        Key key = e.Key == Key.System ? e.SystemKey : e.Key;
        return key is Key.OemPlus or Key.OemMinus or Key.Add or Key.Subtract or Key.D0 or Key.NumPad0;
    }

    private void SuppressPostSubmitKey(KeyEventArgs e)
    {
        if (e.Key == Key.PrintScreen || e.SystemKey == Key.PrintScreen)
        {
            ClearClipboardQuietly();
        }
        ResetWebViewZoom(StudioView);
        StatusText.Text = "Exam submitted. Screen is locked.";
        e.Handled = true;
    }

    private static void ClearClipboardQuietly()
    {
        try
        {
            Clipboard.Clear();
        }
        catch
        {
            // Clipboard access can be temporarily locked by Windows.
        }
    }

    private static void ResetWebViewZoom(WebView2 view)
    {
        try
        {
            if (Math.Abs(view.ZoomFactor - 1.0) > 0.001)
            {
                view.ZoomFactor = 1.0;
            }
        }
        catch
        {
            // WebView may not be initialized yet.
        }
    }

    private async void MainWindowClosing(object? sender, CancelEventArgs e)
    {
        if (!_examActive || _submitted) return;
        e.Cancel = true;
        NativeExamMessage.Text = "Exam is active. Submit or wait for server close before exiting.";
        EnsureExamFullScreen();
        await FlagInterruptionQuietlyAsync();
    }

    protected override void OnClosed(EventArgs e)
    {
        SystemEvents.SessionSwitch -= SystemEventsSessionSwitch;
        SystemEvents.PowerModeChanged -= SystemEventsPowerModeChanged;
        base.OnClosed(e);
    }

    private void ToggleFullScreen()
    {
        if (_examActive)
        {
            EnsureExamFullScreen();
            return;
        }
        if (WindowStyle == WindowStyle.None)
        {
            ExitFullScreen();
            StatusText.Text = "Windowed mode";
            return;
        }
        EnterFullScreen(topmost: false);
        StatusText.Text = "Full screen mode";
    }

    private void EnterExamGuard()
    {
        _examActive = true;
        _submitted = false;
        TopShellBar.Visibility = Visibility.Collapsed;
        BottomStatusBar.Visibility = Visibility.Collapsed;
        EnterFullScreen(topmost: true);
        ApplyCaptureProtection(_session?.DesktopCaptureProtectionEnabled ?? true);
        NativeGuard.StartExamGuard();
        _guardTimer.Start();
        _heartbeatTimer.Start();
        _clockTimer.Start();
        GuardTick();
    }

    private void ExitExamGuard()
    {
        _examActive = false;
        _webExamMode = false;
        _guardTimer.Stop();
        _heartbeatTimer.Stop();
        _clockTimer.Stop();
        TopShellBar.Visibility = Visibility.Collapsed;
        BottomStatusBar.Visibility = Visibility.Collapsed;
        NativeGuard.StopExamGuard();
        ClearCaptureProtection();
        Topmost = false;
        ExitFullScreen();
        NativeGuardText.Text = "SECURE MODE";
    }

    private async Task ClearWebViewExamDataAsync()
    {
        try
        {
            if (StudioView.CoreWebView2 == null) return;
            await StudioView.CoreWebView2.Profile.ClearBrowsingDataAsync(CoreWebView2BrowsingDataKinds.AllProfile);
        }
        catch
        {
            // Cleanup is best-effort; server-side attempt invalidation remains authoritative.
        }
    }

    private void EnterFullScreen(bool topmost)
    {
        if (WindowStyle != WindowStyle.None)
        {
            _restoreState = WindowState;
            _restoreStyle = WindowStyle;
            _restoreResizeMode = ResizeMode;
        }
        WindowStyle = WindowStyle.None;
        ResizeMode = ResizeMode.NoResize;
        WindowState = WindowState.Maximized;
        Topmost = topmost;
    }

    private void ExitFullScreen()
    {
        WindowStyle = _restoreStyle;
        ResizeMode = _restoreResizeMode;
        WindowState = _restoreState;
    }

    private void EnsureExamFullScreen()
    {
        if (WindowStyle != WindowStyle.None || WindowState != WindowState.Maximized || !Topmost)
        {
            EnterFullScreen(topmost: true);
        }
    }

    private void GuardTick()
    {
        if (!_examActive) return;
        EnsureExamFullScreen();
        if ((_session?.DesktopCaptureProtectionEnabled ?? true) && !_captureProtectionApplied)
        {
            ApplyCaptureProtection(true);
        }
        GuardSnapshot guard = NativeGuard.Probe();
        if (guard.Loaded && guard.WorkstationLocked && !_nativeLockProbeActive)
        {
            _nativeLockProbeActive = true;
            _ = RegisterIntegrityViolationAsync("windows-lock-native", "Windows workstation lock detected by native guard.");
        }
        if (guard.Loaded && !guard.WorkstationLocked)
        {
            _nativeLockProbeActive = false;
        }
        if (guard.Loaded && (guard.RemoteSession || guard.DebuggerAttached || guard.WorkstationLocked))
        {
            NativeGuardText.Text = _captureProtectionApplied ? "SECURE MODE - CAPTURE BLOCKED" : "SECURE MODE";
            NativeGuardText.Foreground = Brushes.OrangeRed;
        }
        else
        {
            NativeGuardText.Text = _captureProtectionApplied ? "SECURE MODE - CAPTURE BLOCKED" : "SECURE MODE";
            NativeGuardText.Foreground = new SolidColorBrush(Color.FromRgb(150, 231, 215));
        }
    }

    private async Task HeartbeatAsync()
    {
        if (!_examActive || _session == null) return;
        try
        {
            await _api.HeartbeatAsync(_session.ExamSessionToken);
        }
        catch
        {
            NativeExamMessage.Text = "";
        }
    }

    private async Task FlagInterruptionQuietlyAsync()
    {
        if (_session == null) return;
        try
        {
            await _api.FlagInterruptionAsync(_session.ExamSessionToken);
        }
        catch
        {
            // If the server is already closed, the guard still keeps the local window controlled.
        }
    }

    private void SystemEventsSessionSwitch(object sender, SessionSwitchEventArgs e)
    {
        if (e.Reason != SessionSwitchReason.SessionLock) return;
        Dispatcher.InvokeAsync(() =>
            RegisterIntegrityViolationAsync("windows-lock", "Candidate locked Windows during the active exam."));
    }

    private void SystemEventsPowerModeChanged(object sender, PowerModeChangedEventArgs e)
    {
        if (e.Mode == PowerModes.Suspend)
        {
            _powerSuspendSignalSeen = true;
            Dispatcher.InvokeAsync(() =>
                RegisterIntegrityViolationAsync("system-sleep", "Candidate attempted Windows sleep/suspend during the active exam."));
            return;
        }
        if (e.Mode == PowerModes.Resume && !_powerSuspendSignalSeen)
        {
            Dispatcher.InvokeAsync(() =>
                RegisterIntegrityViolationAsync("system-resume", "System resumed during the active exam after a missed sleep/suspend signal."));
            return;
        }
        if (e.Mode == PowerModes.Resume)
        {
            _powerSuspendSignalSeen = false;
        }
    }

    private async Task RegisterIntegrityViolationAsync(string kind, string reason)
    {
        if (!_examActive || _session == null || _submitted || _candidateBlockedForRejoin) return;
        DateTimeOffset now = DateTimeOffset.UtcNow;
        if (kind == _lastIntegritySignalKind && now - _lastIntegritySignalAt < TimeSpan.FromSeconds(5))
        {
            return;
        }
        _lastIntegritySignalKind = kind;
        _lastIntegritySignalAt = now;
        _integrityViolationCount++;

        string title = "Secure Exam Warning";
        string message = $"Windows lock/sleep is not allowed during the exam.\n\nWarning {_integrityViolationCount}/3.\n\nReason: {reason}";
        NativeExamMessage.Text = $"Security warning {_integrityViolationCount}/3: lock/sleep is not allowed.";
        EnsureExamFullScreen();

        if (_integrityViolationCount < 3)
        {
            MessageBox.Show(this, message, title, MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        await BlockCandidateForRejoinApprovalAsync(reason);
    }

    private async Task BlockCandidateForRejoinApprovalAsync(string reason)
    {
        if (_candidateBlockedForRejoin || _session == null) return;
        _candidateBlockedForRejoin = true;
        string finalReason = $"Candidate reached 3/3 secure desktop violations. Last event: {reason}";
        try
        {
            if (_webExamMode && StudioView.CoreWebView2 != null)
            {
                await StudioView.CoreWebView2.ExecuteScriptAsync("""
                    (async () => {
                      try {
                        if (typeof saveServerCurrentState === 'function') await saveServerCurrentState();
                        if (typeof flushServerExamSaves === 'function') await flushServerExamSaves();
                      } catch (_) {}
                    })();
                    """);
            }
            else
            {
                await SaveCurrentAnswerAsync(markedForReview: false, forceReviewStatus: false);
            }
        }
        catch
        {
            // The interruption flag below is the authority; final answer save is best-effort.
        }

        try
        {
            await _api.FlagInterruptionAsync(_session.ExamSessionToken, finalReason);
        }
        catch
        {
            // Admin incident polling still catches stale sessions, but we try to flag immediately.
        }

        _examActive = false;
        _heartbeatTimer.Stop();
        _clockTimer.Stop();
        _guardTimer.Stop();
        ExitExamGuard();
        StudioView.Visibility = Visibility.Collapsed;
        NativeExamRoot.Visibility = Visibility.Visible;
        ShowCompletionScreen(
            "Exam Paused Pending Admin Approval",
            "Your exam was paused because Windows lock/sleep was detected three times. Move out of the exam window and contact the admin/invigilator. You can rejoin only after admin approves a rejoin request.",
            DateTimeOffset.Now);
    }

    private async Task ClockTickAsync()
    {
        if (!_examActive) return;
        if (_webExamMode) return;
        TimeSpan left = _dueAt - DateTimeOffset.UtcNow;
        if (left <= TimeSpan.Zero)
        {
            NativeTimerText.Text = "00:00:00";
            await SubmitNativeExamAsync(autoSubmit: true);
            return;
        }
        NativeTimerText.Text = left.ToString(@"hh\:mm\:ss", CultureInfo.InvariantCulture);
    }

    private async void NativeStartClicked(object sender, RoutedEventArgs e)
    {
        if (_startInProgress || _examActive || _submitted) return;
        if (!_entryDelayComplete)
        {
            UpdateEntryDelay();
            return;
        }

        string registrationId = NativeRegistrationBox.Text.Trim();
        string dob = NormalizeDob(NativeDobPicker.SelectedDate);
        if (string.IsNullOrWhiteSpace(registrationId) || string.IsNullOrWhiteSpace(dob))
        {
            NativeLoginMessage.Text = "Enter registration ID and date of birth.";
            return;
        }

        NativeStartButton.IsEnabled = false;
        _startInProgress = true;
        NativeLoginMessage.Text = "Preparing exam...";
        try
        {
            _session = await _api.StartNativeSessionAsync(registrationId, dob);
            ApplyNativeBranding(_session);
            EnterExamGuard();
            await LaunchWebExamUiAsync(_session, dob);
        }
        catch (ApiException ex)
        {
            string message = DesktopDialogService.Clean(ex.Message);
            NativeLoginMessage.Text = message;
            DesktopDialogService.Warning(this, "Exam Start Failed", message);
        }
        catch (Exception ex)
        {
            string message = DesktopDialogService.Clean(ex.Message);
            NativeLoginMessage.Text = "Exam start failed.";
            DesktopDialogService.Warning(this, "Exam Start Failed", message);
        }
        finally
        {
            _startInProgress = false;
            NativeStartButton.IsEnabled = !_examActive && !_submitted;
        }
    }

    private async Task LaunchWebExamUiAsync(NativeExamSessionResponse session, string dateOfBirth)
    {
        _webExamMode = true;
        _dueAt = DateTimeOffset.UtcNow.AddSeconds(Math.Max(1, session.RemainingSeconds));
        NativeExamRoot.Visibility = Visibility.Collapsed;
        StudioView.Visibility = Visibility.Visible;
        TopShellBar.Visibility = Visibility.Collapsed;
        BottomStatusBar.Visibility = Visibility.Collapsed;
        AddressText.Text = "Secure exam viewer";
        StatusText.Text = "Opening exam viewer...";
        await InitializeBrowserAsync(navigateHome: false);

        var auth = new
        {
            registrationId = session.RegistrationId,
            dateOfBirth,
            examSessionToken = session.ExamSessionToken,
            remainingSeconds = session.RemainingSeconds
        };
        var candidateData = new
        {
            registrationId = session.RegistrationId,
            fullName = session.FullName,
            examCode = session.ExamCode,
            examName = session.ExamName,
            candidateSubmitEnabled = session.CandidateSubmitEnabled,
            desktopCaptureProtectionEnabled = session.DesktopCaptureProtectionEnabled,
            examReadinessTimerEnabled = session.ExamReadinessTimerEnabled,
            examReadinessDurationSeconds = session.ExamReadinessDurationSeconds,
            examLobbyAvailable = true,
            questionPaperUploaded = true,
            examLaunchEnabled = true,
            responseSubmitted = false,
            examUiHeaderTitle = session.ExamUiHeaderTitle,
            examUiHeaderSubtitle = session.ExamUiHeaderSubtitle,
            examUiBannerImageUrl = session.ExamUiBannerImageUrl,
            instituteLogoDataUrl = session.InstituteLogoDataUrl,
            photoDataUrl = session.PhotoDataUrl
        };

        _desktopExamBridgeMessageJson = JsonSerializer.Serialize(new
        {
            type = "native-start-exam",
            auth,
            candidate = candidateData
        }, JsonDefaults.Options);
        _desktopExamBridgePending = true;
        StudioView.Source = new Uri(_clientConfig.BackendUri(), "candidate-exam.html?secureWindow=1&nativeDesktop=1&desktopLaunch=1&clientUiVersion=20260926");
    }

    private async Task LoadQuestionAsync(int displayIndex)
    {
        if (_session == null || _bootstrap == null || _switchingQuestion || _submitted) return;
        if (displayIndex < 1 || displayIndex > _bootstrap.QuestionCount) return;
        _switchingQuestion = true;
        NativeExamMessage.Text = "";
        try
        {
            QuestionResponse? question = await EnsureRenderableQuestionAsync(displayIndex);
            if (question == null)
            {
                NativeExamMessage.Text = "Question is loading. Please try again.";
                return;
            }
            _currentQuestion = question;
            _questionOpenedAt = DateTimeOffset.UtcNow;
            RenderQuestion(_currentQuestion);
            RenderPalette();
            NativeExamMessage.Text = "";
        }
        catch (ApiException ex)
        {
            await HandleSessionErrorAsync(ex);
        }
        catch (Exception ex)
        {
            NativeExamMessage.Text = "Question load failed. Check network and try again.";
            DesktopDialogService.Warning(this, "Question Load Failed", DesktopDialogService.Clean(ex.Message));
        }
        finally
        {
            _switchingQuestion = false;
        }
    }

    private void RenderQuestion(QuestionResponse response)
    {
        _selectedOptionIds.Clear();
        foreach (string optionId in response.State.MarkedOptionIds ?? [])
        {
            _selectedOptionIds.Add(optionId);
        }

        NativeQuestionHeader.Text = $"{response.DisplayQuestionId}    {QuestionType(response.Question)}";
        NativeSectionHeader.Text = response.Section;
        NativeQuestionText.Text = FirstText(response.Question,
            "body", "question_text", "questionText", "content", "text", "question", "statement");

        string questionImage = FirstText(response.Question,
            "question_image", "questionImage", "image", "canvas", "png", "dataUrl", "data_url",
            "questionBaseImage", "question_base_image", "baseImage", "base_image",
            "questionDisplayImage", "question_display_image", "displayImage", "display_image",
            "questionViewerImage", "question_viewer_image", "viewerImage", "viewer_image");
        NativeQuestionImage.Source = ImageSourceFromDataOrUrl(questionImage);
        NativeQuestionImage.Visibility = NativeQuestionImage.Source == null ? Visibility.Collapsed : Visibility.Visible;
        bool hasQuestionText = !string.IsNullOrWhiteSpace(NativeQuestionText.Text);
        bool hasQuestionImage = NativeQuestionImage.Source != null;
        NativeQuestionUnavailablePanel.Visibility = hasQuestionText || hasQuestionImage ? Visibility.Collapsed : Visibility.Visible;

        NativeOptionsStack.Children.Clear();
        NativeNatAnswerBox.Text = response.State.NatAnswer ?? "";
        bool nat = IsNat(response.Question);
        NativeNatPanel.Visibility = nat ? Visibility.Visible : Visibility.Collapsed;
        ApplyNatAnswerVisualState(nat ? response.State.NatAnswer ?? "" : "");
        if (!nat)
        {
            foreach (NativeOption option in Options(response.Question))
            {
                NativeOptionsStack.Children.Add(OptionButton(option, IsMsq(response.Question)));
            }
        }
    }

    private Button OptionButton(NativeOption option, bool multiSelect)
    {
        bool selected = _selectedOptionIds.Contains(option.Id);
        Button button = new()
        {
            Tag = option.Id,
            HorizontalContentAlignment = HorizontalAlignment.Stretch,
            Background = selected ? new SolidColorBrush(Color.FromRgb(214, 236, 255)) : Brushes.White,
            Foreground = Brushes.Black,
            BorderBrush = selected ? Brushes.DarkGreen : Brushes.Black,
            BorderThickness = new Thickness(selected ? 3 : 1),
            Margin = new Thickness(0, 0, 0, 10),
            Padding = new Thickness(14, 10, 14, 10),
            Content = OptionContent(option)
        };
        button.Click += (_, _) =>
        {
            if (multiSelect)
            {
                if (!_selectedOptionIds.Add(option.Id)) _selectedOptionIds.Remove(option.Id);
            }
            else
            {
                _selectedOptionIds.Clear();
                _selectedOptionIds.Add(option.Id);
            }
            if (_currentQuestion != null) RenderQuestion(_currentQuestion with
            {
                State = _currentQuestion.State with { MarkedOptionIds = _selectedOptionIds.ToList() }
            });
        };
        return button;
    }

    private FrameworkElement OptionContent(NativeOption option)
    {
        StackPanel panel = new() { Orientation = Orientation.Horizontal };
        panel.Children.Add(new TextBlock
        {
            Text = $"({option.Label})",
            FontWeight = FontWeights.Bold,
            FontSize = 18,
            Width = 48,
            VerticalAlignment = VerticalAlignment.Top
        });
        StackPanel body = new();
        if (!string.IsNullOrWhiteSpace(option.Text))
        {
            body.Children.Add(new TextBlock
            {
                Text = option.Text,
                FontFamily = new FontFamily("Cambria Math"),
                FontSize = 20,
                TextWrapping = TextWrapping.Wrap
            });
        }
        BitmapImage? image = ImageSourceFromDataOrUrl(option.ImageDataUrl);
        if (image != null)
        {
            Image optionImage = new()
            {
                Source = image,
                Stretch = Stretch.Uniform,
                MaxWidth = 900,
                HorizontalAlignment = HorizontalAlignment.Left,
                Margin = new Thickness(0, 6, 0, 0)
            };
            RenderOptions.SetBitmapScalingMode(optionImage, BitmapScalingMode.HighQuality);
            optionImage.SnapsToDevicePixels = true;
            body.Children.Add(optionImage);
        }
        if (body.Children.Count == 0) body.Children.Add(new TextBlock { Text = "", Foreground = Brushes.Gray });
        panel.Children.Add(body);
        return panel;
    }

    private void RenderPalette()
    {
        NativePalettePanel.Children.Clear();
        if (_bootstrap == null) return;
        foreach (QuestionSummary question in _bootstrap.Questions)
        {
            Button button = new()
            {
                Content = question.DisplayIndex.ToString(CultureInfo.InvariantCulture),
                Width = 44,
                Height = 38,
                Margin = new Thickness(4),
                Padding = new Thickness(0),
                Background = PaletteBrush(question),
                Foreground = Brushes.White,
                BorderBrush = question.DisplayIndex == _currentQuestion?.DisplayIndex ? Brushes.Gold : Brushes.Black,
                BorderThickness = new Thickness(question.DisplayIndex == _currentQuestion?.DisplayIndex ? 3 : 1),
                Tag = question.DisplayIndex
            };
            button.Click += async (_, _) =>
            {
                if (_submitted) return;
                if (button.Tag is int index)
                {
                    if (await SaveCurrentAnswerAsync(markedForReview: false, forceReviewStatus: false))
                    {
                        await LoadQuestionAsync(index);
                    }
                }
            };
            NativePalettePanel.Children.Add(button);
        }
    }

    private static Brush PaletteBrush(QuestionSummary question)
    {
        if (question.MarkedForReview) return new SolidColorBrush(Color.FromRgb(100, 46, 155));
        return question.Status switch
        {
            "answered" => new SolidColorBrush(Color.FromRgb(19, 118, 66)),
            "not_answered" => new SolidColorBrush(Color.FromRgb(170, 49, 39)),
            "review" or "review_marked" => new SolidColorBrush(Color.FromRgb(100, 46, 155)),
            _ => new SolidColorBrush(Color.FromRgb(105, 114, 128))
        };
    }

    private async void NativeSaveNextClicked(object sender, RoutedEventArgs e)
    {
        if (_submitted || _submitInProgress) return;
        if (await SaveCurrentAnswerAsync(markedForReview: false, forceReviewStatus: false))
        {
            await LoadRelativeQuestionAsync(1);
        }
    }

    private async void NativeReviewNextClicked(object sender, RoutedEventArgs e)
    {
        if (_submitted || _submitInProgress) return;
        if (await SaveCurrentAnswerAsync(markedForReview: true, forceReviewStatus: true))
        {
            await LoadRelativeQuestionAsync(1);
        }
    }

    private async void NativeClearClicked(object sender, RoutedEventArgs e)
    {
        if (_submitted || _submitInProgress) return;
        _selectedOptionIds.Clear();
        NativeNatAnswerBox.Text = "";
        bool saved = await SaveCurrentAnswerAsync(markedForReview: false, forceReviewStatus: false);
        if (saved && _currentQuestion != null) RenderQuestion(_currentQuestion with
        {
            State = _currentQuestion.State with { MarkedOptionIds = [], NatAnswer = "" }
        });
    }

    private async void NativeSubmitClicked(object sender, RoutedEventArgs e)
    {
        if (_submitted || _submitInProgress) return;
        if (MessageBox.Show(this, "Submit this exam now?", "Submit Exam", MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes)
        {
            return;
        }
        await SubmitNativeExamAsync(autoSubmit: false);
    }

    private async Task LoadRelativeQuestionAsync(int offset)
    {
        if (_bootstrap == null || _currentQuestion == null) return;
        int next = _currentQuestion.DisplayIndex + offset;
        if (next > _bootstrap.QuestionCount) next = 1;
        if (next < 1) next = _bootstrap.QuestionCount;
        await LoadQuestionAsync(next);
    }

    private async Task<bool> SaveCurrentAnswerAsync(bool markedForReview, bool forceReviewStatus)
    {
        NativeAnswerSnapshot? snapshot = CaptureCurrentAnswerSnapshot(markedForReview, forceReviewStatus);
        if (snapshot == null) return true;
        return await PersistAnswerSnapshotAsync(snapshot);
    }

    private NativeAnswerSnapshot? CaptureCurrentAnswerSnapshot(bool markedForReview, bool forceReviewStatus)
    {
        if (_session == null || _currentQuestion == null) return null;
        long spent = Math.Max(0, (long)(DateTimeOffset.UtcNow - _questionOpenedAt).TotalMilliseconds);
        bool nat = IsNat(_currentQuestion.Question);
        string natAnswer = nat ? NativeNatAnswerBox.Text.Trim() : "";
        List<string> markedOptionIds = nat ? [] : _selectedOptionIds.ToList();
        bool answered = nat ? !string.IsNullOrWhiteSpace(natAnswer) : markedOptionIds.Count > 0;
        string status = forceReviewStatus ? (answered ? "review_marked" : "review") : (answered ? "answered" : "not_answered");
        return new NativeAnswerSnapshot(
            _session.ExamSessionToken,
            _currentQuestion.DisplayIndex,
            _currentQuestion.Question,
            markedOptionIds,
            natAnswer,
            status,
            markedForReview,
            spent);
    }

    private async Task<bool> PersistAnswerSnapshotAsync(NativeAnswerSnapshot snapshot)
    {
        await _answerSaveLock.WaitAsync();
        try
        {
            SetNativeSaveBusy(true);
            AnswerResponse saved = await _api.AnswerAsync(
                snapshot.ExamSessionToken,
                snapshot.DisplayIndex,
                snapshot.MarkedOptionIds,
                snapshot.NatAnswer,
                snapshot.Status,
                snapshot.MarkedForReview,
                snapshot.TimeSpentMs);
            if (_paper.TryGetValue(snapshot.DisplayIndex, out QuestionResponse? cachedQuestion))
            {
                _paper[snapshot.DisplayIndex] = cachedQuestion with { State = saved.State };
            }
            if (_currentQuestion?.DisplayIndex == snapshot.DisplayIndex)
            {
                _currentQuestion = _currentQuestion with { State = saved.State };
            }
            UpdateQuestionSummary(snapshot.DisplayIndex, snapshot.Status, snapshot.MarkedForReview);
            RenderPalette();
            return true;
        }
        catch (ApiException ex)
        {
            await HandleSessionErrorAsync(ex);
            return false;
        }
        catch (Exception ex)
        {
            NativeExamMessage.Text = "Answer save failed. Check network before moving ahead.";
            DesktopDialogService.Warning(this, "Answer Save Failed", DesktopDialogService.Clean(ex.Message));
            return false;
        }
        finally
        {
            if (!_submitInProgress && !_submitted)
            {
                SetNativeSaveBusy(false);
            }
            _answerSaveLock.Release();
        }
    }

    private async Task RefreshBootstrapAsync()
    {
        if (_session == null) return;
        _bootstrap = await _api.BootstrapAsync(_session.ExamSessionToken);
        RenderPalette();
    }

    private void NativeInstructionsClicked(object sender, RoutedEventArgs e)
    {
        MessageBox.Show(this,
            "Read every question carefully.\n\nUse Save And Next to store your response.\nUse Mark For Review And Next when you want to revisit a question.\nSubmit only after final review.",
            "Instructions",
            MessageBoxButton.OK,
            MessageBoxImage.Information);
    }

    private async void NativeCalculatorClicked(object sender, RoutedEventArgs e)
    {
        Window calculatorWindow = new()
        {
            Owner = this,
            Title = "Scientific Calculator",
            Width = 540,
            Height = 420,
            MinWidth = 520,
            MinHeight = 390,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Background = Brushes.White
        };
        WebView2 calculatorView = new();
        calculatorWindow.Content = calculatorView;
        calculatorWindow.Show();
        try
        {
            await calculatorView.EnsureCoreWebView2Async();
            calculatorView.CoreWebView2.Settings.AreBrowserAcceleratorKeysEnabled = false;
            calculatorView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
            calculatorView.CoreWebView2.Settings.AreDefaultScriptDialogsEnabled = false;
            calculatorView.CoreWebView2.Settings.AreDevToolsEnabled = false;
            calculatorView.CoreWebView2.Settings.IsZoomControlEnabled = false;
            calculatorView.CoreWebView2.Settings.IsPinchZoomEnabled = false;
            calculatorView.ZoomFactor = 1.0;
            calculatorView.ZoomFactorChanged += (_, _) => ResetWebViewZoom(calculatorView);
            await calculatorView.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync(SecureExamZoomLockScript);
            calculatorView.CoreWebView2.ScriptDialogOpening += (_, args) =>
                DesktopDialogService.HandleScriptDialog(this, args, "Scientific Calculator");
            calculatorView.CoreWebView2.WebMessageReceived += (_, args) =>
            {
                string payload = args.WebMessageAsJson ?? "";
                if (payload.Contains("legacy-calculator-close", StringComparison.OrdinalIgnoreCase))
                {
                    calculatorWindow.Close();
                }
            };
            calculatorView.Source = new Uri(_clientConfig.BackendUri(), "assets/legacy-scientific-calculator.html");
        }
        catch (Exception ex)
        {
            calculatorWindow.Close();
            DesktopDialogService.Warning(this, "Calculator", DesktopDialogService.Clean(ex.Message));
        }
    }

    private async void NativeQuestionPaperClicked(object sender, RoutedEventArgs e)
    {
        if (_bootstrap == null || _paper.Count == 0)
        {
            MessageBox.Show(this, "Question paper is not loaded yet.", "Question Paper", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        await EnsureRenderablePaperAsync();
        Window paperWindow = new()
        {
            Owner = this,
            Title = "Question Paper",
            Width = Math.Min(1100, ActualWidth - 80),
            Height = Math.Min(760, ActualHeight - 80),
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Background = Brushes.White
        };
        ScrollViewer scroll = new() { VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
        StackPanel root = new() { Margin = new Thickness(20) };
        root.Children.Add(new TextBlock
        {
            Text = $"{NativeExamTitleText.Text} - Question Paper",
            FontSize = 24,
            FontWeight = FontWeights.Bold,
            Foreground = new SolidColorBrush(Color.FromRgb(9, 26, 49)),
            Margin = new Thickness(0, 0, 0, 14)
        });
        foreach (QuestionResponse question in _paper.Values.OrderBy(item => item.DisplayIndex))
        {
            root.Children.Add(QuestionPaperPreviewBlock(question));
        }
        scroll.Content = root;
        paperWindow.Content = scroll;
        paperWindow.ShowDialog();
    }

    private async Task<QuestionResponse?> EnsureRenderableQuestionAsync(int displayIndex)
    {
        if (_session == null) return null;
        if (_paper.TryGetValue(displayIndex, out QuestionResponse? cached) && IsRenderableQuestion(cached.Question))
        {
            return cached;
        }
        if (!_paperReloadedForRender)
        {
            await ReloadNativePaperAsync();
            if (_paper.TryGetValue(displayIndex, out cached) && IsRenderableQuestion(cached.Question))
            {
                return cached;
            }
        }
        QuestionResponse fresh = await _api.QuestionAsync(_session.ExamSessionToken, displayIndex);
        _paper[displayIndex] = fresh;
        return fresh;
    }

    private async Task EnsureRenderablePaperAsync()
    {
        if (_session == null || _bootstrap == null) return;
        if (_paper.Values.Any(question => !IsRenderableQuestion(question.Question)))
        {
            await ReloadNativePaperAsync();
        }
    }

    private async Task ReloadNativePaperAsync()
    {
        if (_session == null) return;
        PaperResponse paper = await _api.PaperAsync(_session.ExamSessionToken);
        _bootstrap = BootstrapFromPaper(paper);
        _paper.Clear();
        foreach (QuestionResponse question in paper.Paper ?? [])
        {
            _paper[question.DisplayIndex] = question;
        }
        _paperReloadedForRender = true;
        RenderPalette();
    }

    private Border QuestionPaperPreviewBlock(QuestionResponse response)
    {
        StackPanel panel = new();
        panel.Children.Add(new TextBlock
        {
            Text = $"{response.DisplayQuestionId}    {QuestionType(response.Question)}    {response.Section}",
            FontWeight = FontWeights.Bold,
            FontSize = 16,
            Foreground = new SolidColorBrush(Color.FromRgb(9, 26, 49)),
            Margin = new Thickness(0, 0, 0, 8)
        });
        string body = FirstText(response.Question, "body", "question_text", "questionText", "content", "text", "statement");
        if (!string.IsNullOrWhiteSpace(body))
        {
            panel.Children.Add(new TextBlock
            {
                Text = body,
                FontFamily = new FontFamily("Cambria Math"),
                FontSize = 18,
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 8)
            });
        }
        string questionImage = FirstText(response.Question,
            "question_image", "questionImage", "question_png", "image", "canvas", "png", "dataUrl", "data_url",
            "questionBaseImage", "question_base_image", "baseImage", "base_image",
            "questionDisplayImage", "question_display_image", "displayImage", "display_image",
            "questionViewerImage", "question_viewer_image", "viewerImage", "viewer_image");
        BitmapImage? image = ImageSourceFromDataOrUrl(questionImage);
        if (image != null)
        {
            Image questionPreviewImage = new()
            {
                Source = image,
                Stretch = Stretch.Uniform,
                HorizontalAlignment = HorizontalAlignment.Left,
                MaxWidth = 900,
                MaxHeight = 260,
                Margin = new Thickness(0, 0, 0, 10)
            };
            RenderOptions.SetBitmapScalingMode(questionPreviewImage, BitmapScalingMode.HighQuality);
            questionPreviewImage.SnapsToDevicePixels = true;
            panel.Children.Add(questionPreviewImage);
        }
        if (!IsNat(response.Question))
        {
            foreach (NativeOption option in Options(response.Question))
            {
                panel.Children.Add(QuestionPaperOptionPreview(option));
            }
        }
        return new Border
        {
            BorderBrush = Brushes.Black,
            BorderThickness = new Thickness(1),
            Background = Brushes.White,
            Padding = new Thickness(12),
            Margin = new Thickness(0, 0, 0, 14),
            Child = panel
        };
    }

    private FrameworkElement QuestionPaperOptionPreview(NativeOption option)
    {
        StackPanel row = new() { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 4, 0, 0) };
        row.Children.Add(new TextBlock
        {
            Text = $"({option.Label})",
            Width = 42,
            FontWeight = FontWeights.Bold,
            FontSize = 15
        });
        StackPanel body = new();
        if (!string.IsNullOrWhiteSpace(option.Text))
        {
            body.Children.Add(new TextBlock
            {
                Text = option.Text,
                FontFamily = new FontFamily("Cambria Math"),
                FontSize = 16,
                TextWrapping = TextWrapping.Wrap
            });
        }
        BitmapImage? image = ImageSourceFromDataOrUrl(option.ImageDataUrl);
        if (image != null)
        {
            Image optionPreviewImage = new()
            {
                Source = image,
                Stretch = Stretch.Uniform,
                HorizontalAlignment = HorizontalAlignment.Left,
                MaxWidth = 760,
                MaxHeight = 120
            };
            RenderOptions.SetBitmapScalingMode(optionPreviewImage, BitmapScalingMode.HighQuality);
            optionPreviewImage.SnapsToDevicePixels = true;
            body.Children.Add(optionPreviewImage);
        }
        row.Children.Add(body);
        return row;
    }

    private void ApplyNativeBranding(NativeExamSessionResponse session)
    {
        string title = string.IsNullOrWhiteSpace(session.ExamUiHeaderTitle) ? session.ExamName : session.ExamUiHeaderTitle;
        NativeExamTitleText.Text = title;
        NativeInstituteLineText.Text = string.IsNullOrWhiteSpace(session.ExamUiHeaderSubtitle)
            ? "Institute Name"
            : session.ExamUiHeaderSubtitle;
        BitmapImage? logo = SetImageSource(NativeInstituteLogoImage,
            FirstNonBlank(session.ExamUiBannerImageUrl, session.InstituteLogoDataUrl));
        AdjustNativeLogoFrame(logo);
        NativeInstituteLogoFallback.Visibility = NativeInstituteLogoImage.Source == null ? Visibility.Visible : Visibility.Collapsed;
        NativeCandidateNameText.Text = string.IsNullOrWhiteSpace(session.FullName) ? "Candidate" : session.FullName;
        NativeCandidateRegText.Text = string.IsNullOrWhiteSpace(session.RegistrationId)
            ? "Application No."
            : "Application No. " + session.RegistrationId;
        SetImageSource(NativeCandidatePhotoImage, session.PhotoDataUrl);
        NativeCandidatePhotoFallback.Visibility = NativeCandidatePhotoImage.Source == null ? Visibility.Visible : Visibility.Collapsed;
    }

    private void ApplyNativePublicBranding(PublicExamResponse exam)
    {
        string title = string.IsNullOrWhiteSpace(exam.ExamUiHeaderTitle) ? exam.Name : exam.ExamUiHeaderTitle;
        if (!string.IsNullOrWhiteSpace(title))
        {
            NativeExamTitleText.Text = title;
        }
        string banner = FirstNonBlank(exam.ExamUiBannerImageUrl, exam.PortalTopBannerImageUrl, exam.PortalHeroImageUrl);
        BitmapImage? logo = SetImageSource(NativeInstituteLogoImage, banner);
        AdjustNativeLogoFrame(logo);
        NativeInstituteLogoFallback.Visibility = NativeInstituteLogoImage.Source == null ? Visibility.Visible : Visibility.Collapsed;
        _entryDelayEnabled = exam.DesktopEntryTimerEnabled;
        _entryDelaySeconds = Math.Max(0, exam.DesktopEntryDelaySeconds);
        StartEntryDelay();
    }

    private static string FirstNonBlank(params string[] values)
    {
        foreach (string value in values)
        {
            if (!string.IsNullOrWhiteSpace(value)) return value;
        }
        return "";
    }

    private BitmapImage? SetImageSource(Image image, string source)
    {
        BitmapImage? bitmap = ImageSourceFromDataOrUrl(source);
        image.Source = bitmap;
        return bitmap;
    }

    private void AdjustNativeLogoFrame(BitmapImage? logo)
    {
        if (logo == null || logo.PixelWidth <= 0 || logo.PixelHeight <= 0)
        {
            NativeInstituteLogoFrame.ClearValue(WidthProperty);
            NativeInstituteLogoFrame.ClearValue(HeightProperty);
            NativeInstituteLogoFrame.HorizontalAlignment = HorizontalAlignment.Stretch;
            NativeInstituteLogoFrame.VerticalAlignment = VerticalAlignment.Stretch;
            NativeInstituteLogoFrame.Margin = new Thickness(0);
            NativeInstituteLogoFrame.Background = new SolidColorBrush(Color.FromRgb(9, 26, 49));
            NativeInstituteLogoFrame.BorderBrush = new SolidColorBrush(Color.FromRgb(9, 26, 49));
            NativeInstituteLogoImage.Stretch = Stretch.Uniform;
            NativeInstituteLogoImage.Margin = new Thickness(0);
            return;
        }

        double aspect = (double)logo.PixelWidth / logo.PixelHeight;
        bool squareLogo = aspect >= 0.82 && aspect <= 1.22;
        if (squareLogo)
        {
            NativeInstituteLogoFrame.Width = 54;
            NativeInstituteLogoFrame.Height = 54;
            NativeInstituteLogoFrame.HorizontalAlignment = HorizontalAlignment.Left;
            NativeInstituteLogoFrame.VerticalAlignment = VerticalAlignment.Center;
            NativeInstituteLogoFrame.Margin = new Thickness(18, 0, 0, 0);
            NativeInstituteLogoFrame.Background = new SolidColorBrush(Color.FromRgb(9, 26, 49));
            NativeInstituteLogoFrame.BorderBrush = new SolidColorBrush(Color.FromRgb(9, 26, 49));
            NativeInstituteLogoImage.Stretch = Stretch.Uniform;
            NativeInstituteLogoImage.Margin = new Thickness(4);
            return;
        }

        NativeInstituteLogoFrame.ClearValue(WidthProperty);
        NativeInstituteLogoFrame.ClearValue(HeightProperty);
        NativeInstituteLogoFrame.HorizontalAlignment = HorizontalAlignment.Stretch;
        NativeInstituteLogoFrame.VerticalAlignment = VerticalAlignment.Stretch;
        NativeInstituteLogoFrame.Margin = new Thickness(0);
        NativeInstituteLogoFrame.Background = new SolidColorBrush(Color.FromRgb(9, 26, 49));
        NativeInstituteLogoFrame.BorderBrush = new SolidColorBrush(Color.FromRgb(9, 26, 49));
        NativeInstituteLogoImage.Stretch = Stretch.Uniform;
        NativeInstituteLogoImage.Margin = new Thickness(0);
    }

    private static BootstrapResponse BootstrapFromPaper(PaperResponse paper)
    {
        return new BootstrapResponse(
            paper.ExamCode,
            paper.RegistrationId,
            paper.QuestionCount,
            paper.DueAt,
            paper.Sections ?? [],
            paper.Questions ?? []);
    }

    private void UpdateQuestionSummary(int displayIndex, string status, bool markedForReview)
    {
        if (_bootstrap == null) return;
        List<QuestionSummary> updated = _bootstrap.Questions
            .Select(question => question.DisplayIndex == displayIndex
                ? question with { Status = status, MarkedForReview = markedForReview }
                : question)
            .ToList();
        _bootstrap = _bootstrap with { Questions = updated };
    }

    private void SetNativeSaveBusy(bool busy)
    {
        NativeSaveNextButton.IsEnabled = !busy;
        NativeReviewNextButton.IsEnabled = !busy;
        NativeClearButton.IsEnabled = !busy;
        NativeSubmitButton.IsEnabled = !busy;
        NativePalettePanel.IsEnabled = !busy;
    }

    private async Task SubmitNativeExamAsync(bool autoSubmit)
    {
        if (_session == null || _submitted || _submitInProgress) return;
        _submitInProgress = true;
        SetNativeSaveBusy(true);
        NativeExamMessage.Text = autoSubmit ? "Time ended. Submitting exam..." : "Submitting exam...";
        try
        {
            if (!await SaveCurrentAnswerAsync(markedForReview: false, forceReviewStatus: false))
            {
                NativeExamMessage.Text = "Submit stopped because the current answer could not be saved. Check LAN/server connection and retry.";
                return;
            }
            ExamSubmitResponse response = await _api.SubmitAsync(_session.ExamSessionToken);
            _submitted = true;
            ExitExamGuard();
            await ClearWebViewExamDataAsync();
            ShowCompletionScreen(
                "Exam Submitted Successfully",
                autoSubmit ? "Time ended and your exam was submitted successfully. You may now leave the exam window." : "Your exam was submitted successfully. You may now leave the exam window.",
                response.SubmittedAt.ToLocalTime());
            StartInstalledCleanupIfNeeded();
        }
        catch (ApiException ex)
        {
            await HandleSessionErrorAsync(ex);
        }
        catch (Exception ex)
        {
            NativeExamMessage.Text = "Submit failed. Check LAN/server connection and retry.";
            DesktopDialogService.Warning(this, "Submit Failed", DesktopDialogService.Clean(ex.Message));
        }
        finally
        {
            _submitInProgress = false;
            if (!_submitted)
            {
                SetNativeSaveBusy(false);
            }
        }
    }

    private async Task HandleSessionErrorAsync(ApiException ex)
    {
        NativeExamMessage.Text = "";
        if (ex.StatusCode is 400 or 403 or 410 || ex.Message.Contains("expired", StringComparison.OrdinalIgnoreCase)
            || ex.Message.Contains("submitted", StringComparison.OrdinalIgnoreCase)
            || ex.Message.Contains("no longer active", StringComparison.OrdinalIgnoreCase))
        {
            await FlagInterruptionQuietlyAsync();
            _submitted = true;
            ExitExamGuard();
            ShowCompletionScreen("Exam Session Closed", "This exam session is closed. Please contact the invigilator if this was unexpected.", DateTimeOffset.Now);
            StartInstalledCleanupIfNeeded();
        }
    }

    private void NatAnswerPreviewTextInput(object sender, TextCompositionEventArgs e)
    {
        if (sender is not TextBox textBox)
        {
            e.Handled = true;
            return;
        }
        e.Handled = !TryComposeNatText(textBox, e.Text, out _);
    }

    private void NativeNatAnswerPasting(object sender, DataObjectPastingEventArgs e)
    {
        if (sender is not TextBox textBox)
        {
            e.CancelCommand();
            return;
        }
        if (!e.DataObject.GetDataPresent(DataFormats.Text))
        {
            e.CancelCommand();
            return;
        }
        string pasted = e.DataObject.GetData(DataFormats.Text) as string ?? "";
        if (!TryComposeNatText(textBox, pasted, out _))
        {
            e.CancelCommand();
        }
    }

    private void NativeNatAnswerTextChanged(object sender, TextChangedEventArgs e)
    {
        ApplyNatAnswerVisualState(NativeNatAnswerBox.Text);
    }

    private void NativeNatKeyClicked(object sender, RoutedEventArgs e)
    {
        if (sender is not Button button) return;
        string text = button.Content?.ToString() ?? "";
        InsertNatText(text);
    }

    private void NativeNatBackspaceClicked(object sender, RoutedEventArgs e)
    {
        int start = NativeNatAnswerBox.SelectionStart;
        int length = NativeNatAnswerBox.SelectionLength;
        if (length > 0)
        {
            NativeNatAnswerBox.Text = NativeNatAnswerBox.Text.Remove(start, length);
            NativeNatAnswerBox.SelectionStart = start;
            return;
        }
        if (start <= 0) return;
        NativeNatAnswerBox.Text = NativeNatAnswerBox.Text.Remove(start - 1, 1);
        NativeNatAnswerBox.SelectionStart = start - 1;
    }

    private void NativeNatClearClicked(object sender, RoutedEventArgs e)
    {
        NativeNatAnswerBox.Text = "";
        ApplyNatAnswerVisualState(NativeNatAnswerBox.Text);
        NativeNatAnswerBox.Focus();
    }

    private void InsertNatText(string text)
    {
        if (string.IsNullOrEmpty(text)) return;
        if (!TryComposeNatText(NativeNatAnswerBox, text, out string proposed)) return;
        int start = NativeNatAnswerBox.SelectionStart;
        NativeNatAnswerBox.Text = proposed;
        NativeNatAnswerBox.SelectionStart = start + text.Length;
        ApplyNatAnswerVisualState(NativeNatAnswerBox.Text);
        NativeNatAnswerBox.Focus();
    }

    private static bool TryComposeNatText(TextBox textBox, string insertedText, out string proposed)
    {
        int start = textBox.SelectionStart;
        int length = textBox.SelectionLength;
        string current = textBox.Text ?? "";
        if (length > 0)
        {
            current = current.Remove(start, length);
        }
        proposed = current.Insert(start, insertedText ?? "");
        return IsValidNatDraft(proposed);
    }

    private static bool IsValidNatDraft(string value)
    {
        string safe = value ?? "";
        if (safe.Length > 32) return false;
        return NatDraftPattern.IsMatch(safe);
    }

    private void ApplyCaptureProtection(bool enabled)
    {
        if (!enabled)
        {
            ClearCaptureProtection();
            return;
        }
        IntPtr hwnd = new WindowInteropHelper(this).Handle;
        if (hwnd == IntPtr.Zero) return;
        if (SetWindowDisplayAffinity(hwnd, WdaExcludeFromCapture) || SetWindowDisplayAffinity(hwnd, WdaMonitor))
        {
            _captureProtectionApplied = true;
            NativeGuardText.Text = "SECURE MODE - CAPTURE BLOCKED";
        }
    }

    private void ClearCaptureProtection()
    {
        if (!_captureProtectionApplied) return;
        IntPtr hwnd = new WindowInteropHelper(this).Handle;
        if (hwnd != IntPtr.Zero)
        {
            SetWindowDisplayAffinity(hwnd, WdaNone);
        }
        _captureProtectionApplied = false;
    }

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetWindowDisplayAffinity(IntPtr hWnd, uint dwAffinity);

    private const string SecureExamZoomLockScript = """
        (() => {
          const block = event => {
            if (!event) return;
            const key = String(event.key || '').toLowerCase();
            const zoomKey = event.ctrlKey && ['+', '-', '=', '0'].includes(key);
            const wheelZoom = event.type === 'wheel' && event.ctrlKey;
            const gestureZoom = event.type && event.type.startsWith('gesture');
            if (!zoomKey && !wheelZoom && !gestureZoom) return;
            event.preventDefault();
            event.stopPropagation();
          };
          document.addEventListener('keydown', block, true);
          document.addEventListener('wheel', block, { capture: true, passive: false });
          document.addEventListener('gesturestart', block, true);
          document.addEventListener('gesturechange', block, true);
          document.addEventListener('gestureend', block, true);
          const viewport = document.querySelector('meta[name="viewport"]') || document.createElement('meta');
          viewport.name = 'viewport';
          viewport.content = 'width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no';
          if (!viewport.parentNode) document.head.appendChild(viewport);
        })();
        """;

    private sealed record NativeAnswerSnapshot(
        string ExamSessionToken,
        int DisplayIndex,
        JsonElement Question,
        List<string> MarkedOptionIds,
        string NatAnswer,
        string Status,
        bool MarkedForReview,
        long TimeSpentMs);

    private void ApplyNatAnswerVisualState(string value)
    {
        if (string.IsNullOrWhiteSpace(value) || IsValidNatDraft(value))
        {
            NativeNatAnswerBox.BorderBrush = new SolidColorBrush(Color.FromRgb(171, 196, 235));
            NativeNatAnswerBox.Background = Brushes.White;
            NativeNatAnswerBox.ToolTip = null;
            NativeNatWarningText.Visibility = Visibility.Collapsed;
            NativeNatWarningText.Text = "";
            return;
        }

        NativeNatAnswerBox.BorderBrush = new SolidColorBrush(Color.FromRgb(184, 28, 28));
        NativeNatAnswerBox.Background = new SolidColorBrush(Color.FromRgb(254, 242, 242));
        string message = "Loaded answer format looks invalid. Clear it or rewrite it as a proper numerical value like -0.90, 0.45544, 412, or -2345.";
        NativeNatAnswerBox.ToolTip = message;
        NativeNatWarningText.Text = message;
        NativeNatWarningText.Visibility = Visibility.Visible;
    }

    private static string NormalizeDob(DateTime? selectedDate)
    {
        return selectedDate.HasValue ? selectedDate.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) : "";
    }

    private static bool IsNat(JsonElement question)
    {
        string type = QuestionType(question);
        return type.Contains("NAT", StringComparison.OrdinalIgnoreCase)
               || type.Contains("NUM", StringComparison.OrdinalIgnoreCase);
    }

    private static bool IsMsq(JsonElement question)
    {
        string type = QuestionType(question);
        if (type.Contains("MSQ", StringComparison.OrdinalIgnoreCase)
            || type.Contains("MULTI SELECT", StringComparison.OrdinalIgnoreCase)
            || type.Contains("MULTISELECT", StringComparison.OrdinalIgnoreCase)
            || type.Contains("MULTIPLE SELECT", StringComparison.OrdinalIgnoreCase)
            || type.Contains("MULTIPLE ANSWER", StringComparison.OrdinalIgnoreCase)
            || type.Contains("SELECT ALL", StringComparison.OrdinalIgnoreCase)) {
            return true;
        }
        string answerType = FirstText(question,
            "answer_type", "answerType", "question_nature", "questionNature",
            "response_type", "responseType", "selection_type", "selectionType",
            "option_selection", "optionSelection");
        answerType = answerType.Replace('_', ' ').Replace('-', ' ');
        if (answerType.Contains("MSQ", StringComparison.OrdinalIgnoreCase)
            || answerType.Contains("MULTI SELECT", StringComparison.OrdinalIgnoreCase)
            || answerType.Contains("MULTISELECT", StringComparison.OrdinalIgnoreCase)
            || answerType.Contains("MULTIPLE SELECT", StringComparison.OrdinalIgnoreCase)
            || answerType.Contains("MULTIPLE ANSWER", StringComparison.OrdinalIgnoreCase)
            || answerType.Contains("SELECT ALL", StringComparison.OrdinalIgnoreCase)) {
            return true;
        }
        if (question.TryGetProperty("multiple", out JsonElement multiple) && multiple.ValueKind is JsonValueKind.True) return true;
        if (question.TryGetProperty("multiSelect", out JsonElement multiSelect) && multiSelect.ValueKind is JsonValueKind.True) return true;
        if (question.TryGetProperty("maxSelections", out JsonElement maxSelections)
            && maxSelections.ValueKind == JsonValueKind.Number
            && maxSelections.TryGetInt32(out int max)
            && max > 1) {
            return true;
        }
        if (HasArrayWithMultipleValues(question, "correct_options", "correctOptions", "correct_option_ids", "correctOptionIds")
            || HasArrayWithMultipleValues(question, "answers", "answerOptions", "answer_options")
            || HasTruthy(question, "multiple", "multiSelect", "multipleAnswers", "multiple_answers", "allowMultiple", "allow_multiple")
            || HasNumericGreaterThan(question, 1, "maxSelections", "max_selections", "maximumSelections", "maximum_selections"))
        {
            return true;
        }
        return false;
    }

    private static string QuestionType(JsonElement question)
    {
        string type = FirstText(question,
            "question_type", "questionType", "type",
            "answer_type", "answerType",
            "question_nature", "questionNature",
            "response_type", "responseType",
            "selection_type", "selectionType")
            .Replace('_', ' ')
            .Replace('-', ' ')
            .ToUpperInvariant();
        if (string.IsNullOrWhiteSpace(type) || type == "QUESTION")
        {
            type = FirstNestedText(question,
                    ["selection_meta", "selectionMeta", "metadata", "meta"],
                    ["question_type", "questionType", "type", "answer_type", "answerType", "selection_type", "selectionType"])
                .Replace('_', ' ')
                .Replace('-', ' ')
                .ToUpperInvariant();
        }
        return string.IsNullOrWhiteSpace(type) ? "QUESTION" : type;
    }

    private static bool HasTruthy(JsonElement node, params string[] keys)
    {
        if (node.ValueKind != JsonValueKind.Object) return false;
        foreach (string key in keys)
        {
            if (!node.TryGetProperty(key, out JsonElement value)) continue;
            if (value.ValueKind == JsonValueKind.True) return true;
            if (value.ValueKind == JsonValueKind.String && bool.TryParse(value.GetString(), out bool parsed) && parsed) return true;
        }
        return false;
    }

    private static bool HasNumericGreaterThan(JsonElement node, int threshold, params string[] keys)
    {
        if (node.ValueKind != JsonValueKind.Object) return false;
        foreach (string key in keys)
        {
            if (!node.TryGetProperty(key, out JsonElement value)) continue;
            if (value.ValueKind == JsonValueKind.Number && value.TryGetInt32(out int parsed) && parsed > threshold) return true;
            if (value.ValueKind == JsonValueKind.String && int.TryParse(value.GetString(), NumberStyles.Integer, CultureInfo.InvariantCulture, out parsed) && parsed > threshold) return true;
        }
        return false;
    }

    private static bool HasArrayWithMultipleValues(JsonElement node, params string[] keys)
    {
        if (node.ValueKind != JsonValueKind.Object) return false;
        foreach (string key in keys)
        {
            if (!node.TryGetProperty(key, out JsonElement value)) continue;
            if (value.ValueKind == JsonValueKind.Array && value.EnumerateArray().Count(v => !string.IsNullOrWhiteSpace(v.ToString())) > 1) return true;
        }
        return false;
    }

    private static bool IsRenderableQuestion(JsonElement question)
    {
        if (question.ValueKind != JsonValueKind.Object) return false;
        string body = FirstText(question, "body", "question_text", "questionText", "content", "text", "statement", "question");
        string image = FirstText(question,
            "question_image", "questionImage", "question_png", "image", "canvas", "png", "dataUrl", "data_url",
            "questionBaseImage", "question_base_image", "baseImage", "base_image",
            "questionDisplayImage", "question_display_image", "displayImage", "display_image",
            "questionViewerImage", "question_viewer_image", "viewerImage", "viewer_image");
        bool hasOptions = question.TryGetProperty("options", out JsonElement options)
                          && (options.ValueKind == JsonValueKind.Array || options.ValueKind == JsonValueKind.Object)
                          && Options(question).Count > 0;
        return !string.IsNullOrWhiteSpace(body) || !string.IsNullOrWhiteSpace(image) || hasOptions || IsNat(question);
    }

    private static List<NativeOption> Options(JsonElement question)
    {
        if (!question.TryGetProperty("options", out JsonElement options) || options.ValueKind != JsonValueKind.Array)
        {
            if (question.TryGetProperty("options", out JsonElement objectOptions) && objectOptions.ValueKind == JsonValueKind.Object)
            {
                return ObjectOptions(objectOptions, question);
            }
            return [];
        }
        List<NativeOption> result = [];
        int index = 0;
        foreach (JsonElement option in options.EnumerateArray())
        {
            index++;
            string fallbackLabel = ((char)('A' + index - 1)).ToString();
            if (option.ValueKind == JsonValueKind.String)
            {
                result.Add(new NativeOption(fallbackLabel, fallbackLabel, option.GetString() ?? "", ""));
                continue;
            }
            string id = FirstText(option, "id", "optionId", "option_id", "key", "label", "name");
            if (string.IsNullOrWhiteSpace(id)) id = fallbackLabel;
            string label = FirstText(option, "label", "name", "key");
            if (string.IsNullOrWhiteSpace(label)) label = fallbackLabel;
            string text = FirstText(option, "text", "optionText", "option_text", "body", "value");
            string image = FirstText(option, "optionImage", "option_image", "image", "dataUrl", "data_url",
                "optionBaseImage", "option_base_image", "baseImage", "base_image",
                "optionDisplayImage", "option_display_image", "displayImage", "display_image",
                "optionViewerImage", "option_viewer_image", "viewerImage", "viewer_image");
            result.Add(new NativeOption(id, label, text, image));
        }
        return result;
    }

    private static List<NativeOption> ObjectOptions(JsonElement options, JsonElement question)
    {
        List<NativeOption> result = [];
        JsonElement optionIds = default;
        bool hasOptionIds = question.TryGetProperty("option_ids", out optionIds) && optionIds.ValueKind == JsonValueKind.Object;
        foreach (JsonProperty property in options.EnumerateObject())
        {
            string label = property.Name;
            string id = label;
            if (hasOptionIds && optionIds.TryGetProperty(label, out JsonElement mappedId) && mappedId.ValueKind == JsonValueKind.String)
            {
                id = mappedId.GetString()?.Trim() ?? label;
            }
            JsonElement value = property.Value;
            if (value.ValueKind == JsonValueKind.String)
            {
                string raw = value.GetString() ?? "";
                bool image = raw.StartsWith("data:image/", StringComparison.OrdinalIgnoreCase);
                result.Add(new NativeOption(id, label, image ? "" : raw, image ? raw : ""));
            }
            else if (value.ValueKind == JsonValueKind.Object)
            {
                string text = FirstText(value, "text", "optionText", "option_text", "body", "value");
                string image = FirstText(value, "optionImage", "option_image", "image", "dataUrl", "data_url",
                    "optionBaseImage", "option_base_image", "baseImage", "base_image",
                    "optionDisplayImage", "option_display_image", "displayImage", "display_image",
                    "optionViewerImage", "option_viewer_image", "viewerImage", "viewer_image");
                result.Add(new NativeOption(id, label, text, image));
            }
        }
        return result;
    }

    private static string FirstText(JsonElement node, params string[] keys)
    {
        if (node.ValueKind != JsonValueKind.Object) return "";
        foreach (string key in keys)
        {
            if (node.TryGetProperty(key, out JsonElement value))
            {
                if (value.ValueKind == JsonValueKind.String)
                {
                    string text = value.GetString()?.Trim() ?? "";
                    if (!string.IsNullOrWhiteSpace(text)) return text;
                }
                if (value.ValueKind is JsonValueKind.Number or JsonValueKind.True or JsonValueKind.False) return value.ToString();
            }
        }
        return "";
    }

    private static string FirstNestedText(JsonElement node, IReadOnlyCollection<string> parentKeys, IReadOnlyCollection<string> childKeys)
    {
        if (node.ValueKind != JsonValueKind.Object) return "";
        foreach (string parentKey in parentKeys)
        {
            if (!node.TryGetProperty(parentKey, out JsonElement parent) || parent.ValueKind != JsonValueKind.Object) continue;
            string value = FirstText(parent, childKeys.ToArray());
            if (!string.IsNullOrWhiteSpace(value)) return value;
        }
        return "";
    }

    private void ShowCompletionScreen(string title, string body, DateTimeOffset eventTime)
    {
        StudioView.Visibility = Visibility.Collapsed;
        NativeExamRoot.Visibility = Visibility.Visible;
        NativeExamPanel.Visibility = Visibility.Collapsed;
        NativeLoginPanel.Visibility = Visibility.Collapsed;
        NativeCompletionPanel.Visibility = Visibility.Visible;
        NativeCompletionTitle.Text = title;
        NativeCompletionBody.Text = body;
        NativeCompletionTimeText.Text = "System time: " + DateTimeOffset.Now.ToString("yyyy-MM-dd HH:mm:ss zzz", CultureInfo.InvariantCulture);
        NativeCompletionIpText.Text = "Machine IP: " + LocalIpAddress();
        NativeTimerText.Text = eventTime.ToString("HH:mm:ss", CultureInfo.InvariantCulture);
    }

    private void StartInstalledCleanupIfNeeded()
    {
        if (!_clientConfig.ShouldAutoUninstall || _postSubmitCleanupStarted) return;
        _postSubmitCleanupStarted = true;
        NativeCompletionBody.Text += "\n\nThis LAN exam client will close and remove itself from this Windows user.";
        _clientConfig.StartPostSubmitCleanup();
        _ = Task.Run(async () =>
        {
            await Task.Delay(TimeSpan.FromSeconds(8));
            await Dispatcher.InvokeAsync(() =>
            {
                if (!_examActive)
                {
                    _submitted = true;
                    Close();
                }
            });
        });
    }

    private void NativeCloseAfterSubmitClicked(object sender, RoutedEventArgs e)
    {
        _submitted = true;
        Close();
    }

    private static string LocalIpAddress()
    {
        try
        {
            return Dns.GetHostEntry(Dns.GetHostName())
                .AddressList
                .Where(address => address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(address))
                .Select(address => address.ToString())
                .FirstOrDefault() ?? "Not available";
        }
        catch
        {
            return "Not available";
        }
    }

    private static BitmapImage? DataUrlToBitmap(string dataUrl)
    {
        if (string.IsNullOrWhiteSpace(dataUrl)) return null;
        int comma = dataUrl.IndexOf(',', StringComparison.Ordinal);
        string base64 = comma >= 0 ? dataUrl[(comma + 1)..] : dataUrl;
        try
        {
            byte[] bytes = Convert.FromBase64String(base64);
            using MemoryStream stream = new(bytes);
            BitmapImage image = new();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.StreamSource = stream;
            image.EndInit();
            image.Freeze();
            return image;
        }
        catch
        {
            return null;
        }
    }

    private BitmapImage? ImageSourceFromDataOrUrl(string source)
    {
        if (string.IsNullOrWhiteSpace(source)) return null;
        if (source.StartsWith("data:", StringComparison.OrdinalIgnoreCase))
        {
            return DataUrlToBitmap(source);
        }
        try
        {
            Uri uri = source.StartsWith("/", StringComparison.Ordinal)
                ? new Uri(_clientConfig.BackendUri(), source)
                : new Uri(source, UriKind.Absolute);
            BitmapImage image = new();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.UriSource = uri;
            image.EndInit();
            image.Freeze();
            return image;
        }
        catch
        {
            return null;
        }
    }
}
