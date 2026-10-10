$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
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
$desktopProject = Join-Path $desktopRoot "src\QSStudio.Desktop\QSStudio.Desktop.csproj"
$desktopOutput = Join-Path $desktopRoot "artifacts\SecureExamClient"

New-Item -ItemType Directory -Force -Path $nativeBuild | Out-Null
New-Item -ItemType Directory -Force -Path $desktopOutput | Out-Null

& $cmake -S $nativeSource -B $nativeBuild -G "Visual Studio 17 2022" -A x64
& $cmake --build $nativeBuild --config Release

& $dotnet publish $desktopProject -c Release -r win-x64 --self-contained false -o $desktopOutput

$nativeDll = Join-Path $nativeBuild "Release\qsstudio_native_guard.dll"
if (Test-Path $nativeDll) {
    Copy-Item -Force $nativeDll (Join-Path $desktopOutput "qsstudio_native_guard.dll")
}

Write-Host "Desktop build ready:" $desktopOutput
