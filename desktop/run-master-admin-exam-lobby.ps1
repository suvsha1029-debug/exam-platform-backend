$ErrorActionPreference = "Stop"

$desktopRoot = $PSScriptRoot
$output = Join-Path $desktopRoot "artifacts\MasterAdminExamLobby"
$exe = Join-Path $output "MasterAdmin.ExamLobby.exe"

if (-not (Test-Path $exe)) {
    & (Join-Path $desktopRoot "build-all-apps.ps1")
}

Start-Process -FilePath $exe -WorkingDirectory $output
