# Kernel Guard Feasibility

## Decision

An admin-controlled kernel guard is viable only as a **local Windows endpoint product**. The web admin portal can publish a narrowly defined, signed exam policy; it must never issue arbitrary kernel commands. The candidate device verifies and enforces that policy locally.

This is not an instant security switch. A custom kernel driver is high-risk software: a bug can blue-screen a candidate machine, create a privilege-escalation vulnerability, or fail Windows signing requirements. The first production version should rely on Windows security controls and a signed user-mode guard service, with a kernel driver considered only for a demonstrated, unmet requirement.

## What The Admin Can Control

The admin can select a pre-approved `ExamGuardPolicy` for a specific exam:

- full-screen/assigned-access requirement
- whether the candidate client may run
- permitted network endpoints and certificate pins
- permitted USB classes, if the deployment environment is managed
- block state for remote desktop / known remote-control processes
- required platform posture: Secure Boot, BitLocker, Defender, VBS/HVCI
- incident action: warn, lock the client, or end the attempt

The policy is signed by the platform with an asymmetric private key. The candidate guard accepts only signed policies that match its device registration and have a short expiry. It sends only signed health/incident reports back to the server.

## What A Guard Cannot Promise

- A web server cannot remotely enforce kernel controls on a machine without a trusted locally installed agent.
- No Windows software can stop an external camera, another phone, or all physical attacks.
- A candidate with local administrator or physical boot access must be treated as a high-risk endpoint unless the machine is center-managed.
- Blocking Print Screen, Windows shortcuts, or screen recording from user mode is deterrence, not cryptographic protection.

## Recommended Implementation Order

1. **Phase 1: Signed user-mode guard service**
   - Windows service plus C# exam client.
   - Assigned Access / kiosk profile, AppLocker or WDAC allow-list, Defender tamper protection, firewall egress allow-list, Secure Boot and BitLocker posture checks.
   - Admin policy is a small signed JSON document, not a remote-control channel.

2. **Phase 2: Managed-center controls**
   - Intune/MDM or local Group Policy manages USB, removable storage, account privileges, browser/app allow-lists, and the exam network VLAN.
   - Device certificates or mTLS bind each station to the exam server.

3. **Phase 3: Kernel component only if justified**
   - Use a narrowly scoped, independently reviewed and Microsoft-signed driver.
   - Start with telemetry/posture enforcement, not input interception or broad process control.
   - Require crash recovery, a safe disable path for invigilators, audit logs, version rollback, and pilot-center testing.

## Non-Negotiable Security Gates

- Remove development fallback secrets and hard-coded credentials before any external deployment.
- Put the admin/backend on HTTPS with valid certificates; do not expose PostgreSQL publicly.
- Use per-device certificates for desktop consoles rather than one shared secret.
- Keep private keys in the server/OS credential store, never in browser storage or source files.
- Use a separate exam-network VLAN with firewall allow-listing for center deployments.
- Complete a professional security review and legal/privacy review before claiming anti-cheat or kernel-level protection.

## Practical Verdict

For a controlled physical exam center, this is a strong and realistic roadmap. For unmanaged home laptops, it becomes a deterrence and telemetry product rather than a guarantee. The safe next build is the signed user-mode policy/guard service, not a custom kernel driver.
