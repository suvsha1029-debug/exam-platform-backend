$ErrorActionPreference = "Stop"

$desktopRoot = $PSScriptRoot
$output = Join-Path $desktopRoot "artifacts\SecureExamClient"
$exe = Join-Path $output "QSStudio.Desktop.exe"

if (-not (Test-Path $exe)) {
    & (Join-Path $desktopRoot "build-all-apps.ps1")
}

Start-Process -FilePath $exe -WorkingDirectory $output
