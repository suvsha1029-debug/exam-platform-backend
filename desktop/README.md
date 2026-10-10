# QS Studio Desktop

This folder starts the native desktop migration without rewriting the working web app.

## Projects

- `src/QSStudio.Desktop`: C# WPF desktop app with two modes:
  - Native Exam: server-driven native candidate exam client.
  - Studio Browser: WebView2 fallback for the existing web platform.
- `native/QSStudio.NativeGuard`: C++ DLL for remote-session/debugger guard probes.

## Build

```powershell
.\desktop\build-desktop.ps1
```

Output:

```text
desktop\artifacts\QSStudio.Desktop\QSStudio.Desktop.exe
```

To build a LAN installer for other candidate laptops:

```powershell
.\desktop\build-secure-client-setup.ps1 -DefaultBackendUrl "http://192.168.1.25:8081"
```

Output:

```text
desktop\artifacts\SecureExamClientSetup.exe
```

## LAN Exam Setup

1. Connect the admin/server machine and all candidate laptops to the same Wi-Fi/LAN.
2. On the admin/server machine, find the LAN IPv4 address:

   ```powershell
   ipconfig
   ```

   Use the Wi-Fi/Ethernet IPv4, for example `192.168.1.25`.

3. Start the backend so it listens on LAN, not only localhost:

   ```powershell
   .\gradlew.bat bootRun --args="--server.address=0.0.0.0 --server.port=8081"
   ```

4. From another laptop on the same LAN, test:

   ```text
   http://192.168.1.25:8081/candidate-registration.html
   ```

   If it does not load, allow Java/backend through Windows Firewall on the admin machine.

5. Build the installer with that backend URL:

   ```powershell
   .\desktop\build-secure-client-setup.ps1 -DefaultBackendUrl "http://192.168.1.25:8081"
   ```

6. Copy `desktop\artifacts\SecureExamClientSetup.exe` to candidate laptops and run it.
   The installer writes `secure-client-config.json`, creates a Desktop shortcut, and registers an uninstall entry for that Windows user.

7. Candidate opens **Secure Exam Client** from the Desktop shortcut and takes the exam.
   After final submit, installed LAN clients clear local state and launch the uninstall helper.

## Run

Start the Spring Boot backend first, then:

```powershell
.\desktop\run-desktop.ps1
```

## Current Scope

Current native exam scope:

- Candidate enters registration ID + DOB in the C# app.
- Backend issues a one-time native exam attempt token.
- C# app calls `/api/candidate/server-exam/bootstrap`, `/question`, `/answer`, and `/submit`.
- Only one question is fetched at a time.
- Fullscreen guard stays active until submit or server-side close.
- Heartbeat continues during the active attempt.
- WebView studio mode remains available only outside an active native exam.

This is not yet a kernel/kiosk driver. Blocking screen recorders, USB, remote tools, or process access needs a separate audited Windows policy/native security pass.
