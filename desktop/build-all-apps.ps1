$ErrorActionPreference = "Stop"

$desktopRoot = $PSScriptRoot
$dotnet = "C:\Program Files\dotnet\dotnet.exe"
$cmake = "C:\Program Files\CMake\bin\cmake.exe"

if (-not (Test-Path $dotnet)) {
    throw "dotnet.exe was not found at $dotnet"
}
if (-not (Test-Path $cmake)) {
    throw "cmake.exe was not found at $cmake"
}

$nativeSource = Join-Path $desktopRoot "native\QSStudio.NativeGuard"
$nativeBuild = Join-Path $desktopRoot "build\native"
$examProject = Join-Path $desktopRoot "src\QSStudio.Desktop\QSStudio.Desktop.csproj"
$masterProject = Join-Path $desktopRoot "src\MasterAdmin.ExamLobby\MasterAdmin.ExamLobby.csproj"
$examOutput = Join-Path $desktopRoot "artifacts\SecureExamClient"
$masterOutput = Join-Path $desktopRoot "artifacts\MasterAdminExamLobby"

New-Item -ItemType Directory -Force -Path $nativeBuild | Out-Null
New-Item -ItemType Directory -Force -Path $examOutput | Out-Null
New-Item -ItemType Directory -Force -Path $masterOutput | Out-Null

Write-Host "Building native guard..."
& $cmake -S $nativeSource -B $nativeBuild -G "Visual Studio 17 2022" -A x64
& $cmake --build $nativeBuild --config Release

Write-Host "Publishing Secure Exam Client..."
& $dotnet publish $examProject -c Release -r win-x64 --self-contained false -o $examOutput

$nativeDll = Join-Path $nativeBuild "Release\qsstudio_native_guard.dll"
if (Test-Path $nativeDll) {
    Copy-Item -Force $nativeDll (Join-Path $examOutput "qsstudio_native_guard.dll")
}

Write-Host "Publishing Master Admin Exam Lobby..."
& $dotnet publish $masterProject -c Release -r win-x64 --self-contained false -o $masterOutput

Set-Content -Path (Join-Path $examOutput "RUN_SECURE_EXAM_CLIENT.bat") -Value "@echo off`r`ncd /d %~dp0`r`nstart """" ""QSStudio.Desktop.exe""`r`n" -Encoding ASCII
Set-Content -Path (Join-Path $masterOutput "RUN_MASTER_ADMIN_EXAM_LOBBY.bat") -Value "@echo off`r`ncd /d %~dp0`r`nstart """" ""MasterAdmin.ExamLobby.exe""`r`n" -Encoding ASCII

Write-Host ""
Write-Host "Build complete."
Write-Host "Secure Exam Client:" $examOutput
Write-Host "Master Admin Exam Lobby:" $masterOutput
