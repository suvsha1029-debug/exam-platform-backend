# Secure Assessment Conducting Services

An examination-management suite covering registration, candidate corrections,
secure exam delivery, evaluation, answer-key challenges and result publication.

The repository includes the Spring Boot backend, candidate and administrator web
interfaces, Windows desktop clients, question-import tools and automated tests.
It is application source hosting, **not a running examination website**. GitHub
Pages cannot run the Java backend or PostgreSQL database.

## Features

- Candidate registration, login, application review and printable records.
- Admin-controlled correction windows for details, photographs, signatures,
  documents and custom fields, with review before final submission.
- Exam configuration, question papers, response capture and evaluation.
- Answer-key publication, challenges, supporting proofs and resolution.
- Results, score-card PDFs, publication controls and candidate result withholding.
- Master-admin operations and desktop device-enrollment controls.
- Windows secure exam client and operator console.
- Configurable portal branding, notices, FAQs and contact information.

See [product overview](PRODUCT_OVERVIEW.md), [product description](PRODUCT_DESCRIPTION.md)
and [desktop documentation](desktop/README.md) for additional detail.

## Repository Layout

| Path | Contents |
| --- | --- |
| `src/main/java` | Backend services, controllers, security and persistence |
| `src/main/resources/static` | Candidate, admin and master-admin web interfaces |
| `src/test` | Backend tests using an isolated test database |
| `desktop/src` | Windows desktop application source |
| `desktop/native` | Native desktop guard integration |
| `desktop/installer` | Secure-client installer source |
| `desktop/tests` | Browser smoke tests and visual checks |
| `pdf-question-import-studio` | Question-import studio |
| `tools` | Supporting import, operations and build utilities |
| `design-templates` | Design resources |

## Requirements

- JDK 25; use the included Gradle wrapper.
- PostgreSQL with a dedicated application database and user.
- Windows and .NET SDK 8 for the WPF desktop applications.
- Microsoft Edge WebView2 Runtime for WebView2-based desktop surfaces.
- Python for optional viewer/import integrations.
- Node.js, Playwright and Chrome for browser smoke tests.

## Run the Backend

Clone this private repository using an authorized GitHub account:

```powershell
git clone https://github.com/suvsha1029-debug/exam-platform-backend.git
cd exam-platform-backend
```

Create the PostgreSQL database separately, then configure the process environment:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/exam_platform'
$env:DB_USERNAME = 'your_database_user'
$env:DB_PASSWORD = 'your_database_password'
$env:MASTER_ADMIN_EMAIL = 'administrator@example.com'
$env:APP_UPLOAD_DIR = Join-Path $PWD 'uploads'
$env:PUBLIC_BASE_URL = 'http://localhost:8081'
$env:DESKTOP_CONSOLE_ENROLLMENT_TOKEN = 'replace-with-a-long-random-value'
.\gradlew.bat bootRun
```

Open [the local portal](http://localhost:8081/). Persistent uploads are stored in
`APP_UPLOAD_DIR`; back up that directory together with the database.

The checked-in configuration retains the original development paths, including
`D:/exam-platform-backend/uploads` and `D:/VIJRHED/gate_exam_viewer.py`. Override
`APP_UPLOAD_DIR`, `APP_VIEWER_PYTHON_SCRIPT` and `APP_VIEWER_PYTHON_COMMAND` on other
machines. The external viewer script is not part of this repository; workflows
that use it require that integration to be supplied separately.

For mail, configure `MAIL_USERNAME` and `MAIL_PASSWORD` privately. Do not commit
credentials. Environment variables are not automatically loaded from `.env` by
this application.

### Administration

Operator routes are protected by role checks, network restrictions and, by
default, desktop enrollment. Use the desktop master-admin/operator workflow;
do not disable these protections merely to expose the console on the internet.
The Windows launch scripts retain local machine paths and must be reviewed
before use on a new machine. Prefer `gradlew.bat bootRun` for the first setup.

## Build and Test

```powershell
.\gradlew.bat test bootJar --no-daemon
java -jar build\libs\exam-platform-backend-0.0.1-SNAPSHOT.jar
```

The backend test configuration uses H2 instead of the production PostgreSQL
database. Never override tests to point at a live examination database.

Browser smoke tests live in `desktop/tests`. Some currently depend on local
Chrome paths, fixture images, PDF utilities or the original development runtime.
Review the script prerequisites before running them on another machine.

## Desktop Applications and Installers

```powershell
.\desktop\build-desktop.ps1
.\desktop\build-master-lobby.ps1
.\desktop\build-secure-client-setup.ps1 -DefaultBackendUrl 'http://YOUR-SERVER:8081'
```

Read [desktop/README.md](desktop/README.md) before distributing clients. Candidate
machines must be able to reach the backend URL configured in the installer.

Available binary snapshots are attached to [GitHub Releases](https://github.com/suvsha1029-debug/exam-platform-backend/releases),
not committed into source history. Existing installer snapshots may contain a
development/LAN server URL and are not signed production releases. Build a fresh
installer with your own server address before distribution.

## Deployment and Security

- Host the Java application on a suitable server; use PostgreSQL and persistent storage.
- Use HTTPS, a trusted reverse proxy and `SESSION_COOKIE_SECURE=true` for public access.
- Set an explicit admin network allowlist and unique enrollment secrets.
- Replace all development defaults; keep shared-secret fallback disabled.
- Review automatic Hibernate schema updates before a production upgrade.
- Back up and test restoration of both database and uploads before deployment.
- Validate desktop protections on supported exam hardware; they are not a guarantee against cheating.

See [SECURITY.md](SECURITY.md) for reporting and handling sensitive information.

## What Is Intentionally Excluded

Candidate uploads, database contents, logs, browser profiles, saved sessions,
credentials, temporary exports and dependency/build caches are excluded. Their
absence is intentional, not an incomplete source upload. Installer downloads
are provided separately where safe to publish.

## License

No open-source license has been granted. Repository access does not itself grant
permission to redistribute the software or third-party material.
