# ExamShield Secure Browser Lite

ExamShield is the first secure-browser layer for the exam platform.

This prototype intentionally avoids changing the exam UI. It launches Microsoft Edge in a dedicated kiosk-style session that opens only the platform exam entry route.

## Current Mode

- Uses installed Microsoft Edge.
- Opens the candidate exam entry in kiosk/fullscreen app mode.
- Uses a separate temporary browser profile under `tools/examshield/runtime-profile`.
- Clears the temporary profile after the session closes.
- Keeps all actual exam security on the backend: secure page/session tokens, attempt token, CSRF, autosave, offline draft, and one-submit rule.

## Limitations

This is not a native lockdown browser yet. It cannot fully block OS shortcuts, Task Manager, external apps, screen recorders, or network access. It is a secure launcher/kiosk shell for controlled labs and demos.

## Future Native Version

The planned native client should be built as a Windows app with C#/.NET + WebView2:

- fullscreen kiosk WebView
- allowed URL list
- focus-loss and fullscreen-exit logs
- optional process checks
- optional network whitelist mode
- encrypted local draft storage
- upload logs after submit
- optional lab-mode logout/restart

