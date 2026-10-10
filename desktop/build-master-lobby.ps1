$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$project = Join-Path $root 'src\MasterAdmin.ExamLobby\MasterAdmin.ExamLobby.csproj'
$output = Join-Path $root 'artifacts\MasterAdminExamLobby'
dotnet publish $project -c Release -r win-x64 --self-contained false -o $output
Set-Content -Path (Join-Path $output "RUN_MASTER_ADMIN_EXAM_LOBBY.bat") -Value "@echo off`r`ncd /d %~dp0`r`nstart """" ""MasterAdmin.ExamLobby.exe""`r`n" -Encoding ASCII
Write-Host "Built Master Admin Exam Lobby -> $output"
