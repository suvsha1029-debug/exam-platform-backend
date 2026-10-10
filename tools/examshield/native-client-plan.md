# Native Client Plan

Target stack: C#/.NET + WebView2.

## Phase 1

- Single Windows app window.
- Fullscreen kiosk WebView2.
- Open only configured platform URL.
- Disable context menu, dev tools, new window, and external navigation.
- Send focus loss/fullscreen exit events to backend.

## Phase 2

- Local encrypted draft store.
- Upload event logs after submit.
- Optional webcam/screen permission checks.
- Admin-configurable violation threshold.

## Phase 3

- Optional lab mode.
- Windows firewall whitelist mode with explicit admin consent.
- Optional logout/restart after successful upload.

## Safety Rules

- Never self-delete the app.
- Never change network/firewall without explicit lab-mode config.
- Never restart the machine unless admin explicitly enables lab mode.
- Always clear exam package/session data after final submit.
