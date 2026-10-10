$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$configPath = Join-Path $root "examshield.config.json"
if (-not (Test-Path $configPath)) {
    throw "Missing ExamShield config: $configPath"
}

$config = Get-Content -Raw $configPath | ConvertFrom-Json
$edge = $null
foreach ($path in $config.edgePaths) {
    if (Test-Path $path) {
        $edge = $path
        break
    }
}

if (-not $edge) {
    throw "Microsoft Edge was not found. Install Edge or update examshield.config.json."
}

$profile = Join-Path $root $config.profileDir
if ($config.clearProfileOnExit -and (Test-Path $profile)) {
    Remove-Item -LiteralPath $profile -Recurse -Force
}
New-Item -ItemType Directory -Path $profile -Force | Out-Null

try {
    Invoke-WebRequest -UseBasicParsing $config.allowedOrigin -TimeoutSec 5 | Out-Null
} catch {
    throw "Exam backend is not reachable at $($config.allowedOrigin). Start the exam platform first."
}

$arguments = @(
    "--kiosk", $config.examUrl,
    "--edge-kiosk-type=fullscreen",
    "--user-data-dir=$profile",
    "--no-first-run",
    "--disable-extensions",
    "--disable-features=Translate,msEdgeShoppingAssistant,msSidebarSearchAfterSearchWebFor",
    "--overscroll-history-navigation=0"
)

Write-Host "Starting ExamShield Secure Browser Lite..."
Write-Host "URL: $($config.examUrl)"
Write-Host "Close Edge after the exam to return to Windows."

$process = Start-Process -FilePath $edge -ArgumentList $arguments -PassThru
$process.WaitForExit()

if ($config.clearProfileOnExit -and (Test-Path $profile)) {
    Remove-Item -LiteralPath $profile -Recurse -Force
}

Write-Host "ExamShield session closed and temporary profile cleared."
