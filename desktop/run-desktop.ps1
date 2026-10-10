$ErrorActionPreference = "Stop"

$desktopOutput = Join-Path $PSScriptRoot "artifacts\QSStudio.Desktop"
$exe = Join-Path $desktopOutput "QSStudio.Desktop.exe"

if (-not (Test-Path $exe)) {
    & (Join-Path $PSScriptRoot "build-desktop.ps1")
}

Start-Process -FilePath $exe -WorkingDirectory $desktopOutput
