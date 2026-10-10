# QS Studio Native Guard

This C++ project is the native seam for future exam-shell security checks.

Current first-pass exports:

- `QsGuard_GetVersion`
- `QsGuard_IsRemoteSession`
- `QsGuard_IsDebuggerAttached`

It intentionally does not block USB, screen recording, drivers, or processes yet. Those controls need an audited Windows policy/driver design and admin consent.
