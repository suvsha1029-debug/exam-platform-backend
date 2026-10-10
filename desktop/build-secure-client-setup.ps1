param(
    [string]$DefaultBackendUrl = "http://192.168.1.25:8081"
)

$ErrorActionPreference = "Stop"

$desktopRoot = $PSScriptRoot
$installerRoot = Join-Path $desktopRoot "installer"
$artifactDir = Join-Path $desktopRoot "artifacts\SecureExamClient"
$stagingDir = Join-Path $desktopRoot "artifacts\SecureExamClientSetupBuild"
$payloadZip = Join-Path $stagingDir "SecureExamClientPayload.zip"
$setupScriptSource = Join-Path $installerRoot "SecureExamClientSetup.ps1"
$setupScriptStaged = Join-Path $stagingDir "SecureExamClientSetup.ps1"
$sedPath = Join-Path $stagingDir "SecureExamClientSetup.sed"
$setupExe = Join-Path $desktopRoot "artifacts\SecureExamClientSetup.exe"

& (Join-Path $desktopRoot "build-desktop.ps1")

if (-not (Test-Path $artifactDir)) {
    throw "SecureExamClient artifact folder missing: $artifactDir"
}
if (-not (Test-Path $setupScriptSource)) {
    throw "Setup script missing: $setupScriptSource"
}

Remove-Item -LiteralPath $stagingDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null
Remove-Item -LiteralPath $payloadZip -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $artifactDir "*") -DestinationPath $payloadZip -Force
Copy-Item -LiteralPath $setupScriptSource -Destination $setupScriptStaged -Force

$iexpress = Join-Path $env:WINDIR "System32\iexpress.exe"
if (-not (Test-Path $iexpress)) {
    throw "iexpress.exe was not found. Windows IExpress is required to build SecureExamClientSetup.exe."
}

$defaultBackendArg = $DefaultBackendUrl.Replace('"', '')
$sed = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=1
HideExtractAnimation=0
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$setupExe
FriendlyName=Secure Exam Client Setup
AppLaunched=powershell.exe -NoProfile -ExecutionPolicy Bypass -File SecureExamClientSetup.ps1 -BackendUrl "$defaultBackendArg"
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
SourceFiles=SourceFiles
[Strings]
FILE0=SecureExamClientSetup.ps1
FILE1=SecureExamClientPayload.zip
[SourceFiles]
SourceFiles0=$stagingDir
[SourceFiles0]
%FILE0%=
%FILE1%=
"@
$sed | Set-Content -LiteralPath $sedPath -Encoding ASCII

& $iexpress /N /Q $sedPath

for ($i = 0; $i -lt 20 -and -not (Test-Path $setupExe); $i++) {
    Start-Sleep -Milliseconds 250
}

if (-not (Test-Path $setupExe)) {
    throw "SecureExamClientSetup.exe was not created."
}

Write-Host "Secure client setup ready:" $setupExe
Write-Host "Default backend URL baked into setup:" $DefaultBackendUrl
