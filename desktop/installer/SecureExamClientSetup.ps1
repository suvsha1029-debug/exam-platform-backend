param(
    [string]$BackendUrl = "",
    [switch]$NoAutoUninstall
)

$ErrorActionPreference = "Stop"

function Normalize-BackendUrl {
    param([string]$Value)
    $trimmed = ""
    if ($null -ne $Value) {
        $trimmed = $Value.Trim()
    }
    if (-not $trimmed) {
        Add-Type -AssemblyName Microsoft.VisualBasic
        $trimmed = [Microsoft.VisualBasic.Interaction]::InputBox(
            "Enter backend LAN URL. Example: http://192.168.1.25:8081",
            "Secure Exam Client Setup",
            "http://192.168.1.25:8081"
        ).Trim()
    }
    if (-not $trimmed) {
        throw "Backend LAN URL is required."
    }
    if ($trimmed -notmatch '^https?://') {
        $trimmed = "http://$trimmed"
    }
    if ($trimmed.EndsWith('/')) {
        return $trimmed
    }
    return "$trimmed/"
}

$backend = Normalize-BackendUrl $BackendUrl
$installDir = Join-Path $env:LOCALAPPDATA "QSStudio\SecureExamClient"
$supportDir = Join-Path $env:LOCALAPPDATA "QSStudio"
$payloadZip = Join-Path $PSScriptRoot "SecureExamClientPayload.zip"
$uninstallScript = Join-Path $supportDir "uninstall-secure-exam-client.ps1"

if (-not (Test-Path $payloadZip)) {
    throw "Installer payload missing: $payloadZip"
}

New-Item -ItemType Directory -Force -Path $installDir | Out-Null
New-Item -ItemType Directory -Force -Path $supportDir | Out-Null

Get-ChildItem -LiteralPath $installDir -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
Expand-Archive -LiteralPath $payloadZip -DestinationPath $installDir -Force

$config = [ordered]@{
    backendUrl = $backend
    installMode = "lan"
    autoUninstallAfterSubmit = -not $NoAutoUninstall.IsPresent
    uninstallScriptPath = $uninstallScript
}
$config | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $installDir "secure-client-config.json") -Encoding UTF8

@'
param(
    [switch]$Silent,
    [int]$DelayPid = 0
)

$ErrorActionPreference = "SilentlyContinue"
$installDir = Join-Path $env:LOCALAPPDATA "QSStudio\SecureExamClient"
$shortcut = Join-Path ([Environment]::GetFolderPath("Desktop")) "Secure Exam Client.lnk"
$startShortcut = Join-Path ([Environment]::GetFolderPath("Programs")) "Secure Exam Client.lnk"

if ($DelayPid -gt 0) {
    try {
        $process = Get-Process -Id $DelayPid -ErrorAction SilentlyContinue
        if ($process) { Wait-Process -Id $DelayPid -Timeout 20 -ErrorAction SilentlyContinue }
    } catch {}
}

Remove-Item -LiteralPath $shortcut -Force
Remove-Item -LiteralPath $startShortcut -Force
Remove-Item -LiteralPath "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\SecureExamClient" -Recurse -Force
Remove-Item -LiteralPath $installDir -Recurse -Force

if (-not $Silent) {
    Add-Type -AssemblyName PresentationFramework
    [System.Windows.MessageBox]::Show("Secure Exam Client was removed from this Windows user.", "Secure Exam Client")
}
'@ | Set-Content -LiteralPath $uninstallScript -Encoding UTF8

$exePath = Join-Path $installDir "QSStudio.Desktop.exe"
$wsh = New-Object -ComObject WScript.Shell
$desktopShortcut = Join-Path ([Environment]::GetFolderPath("Desktop")) "Secure Exam Client.lnk"
$shortcut = $wsh.CreateShortcut($desktopShortcut)
$shortcut.TargetPath = $exePath
$shortcut.WorkingDirectory = $installDir
$shortcut.Description = "LAN Secure Exam Client"
$shortcut.Save()

$programsShortcut = Join-Path ([Environment]::GetFolderPath("Programs")) "Secure Exam Client.lnk"
$shortcut = $wsh.CreateShortcut($programsShortcut)
$shortcut.TargetPath = $exePath
$shortcut.WorkingDirectory = $installDir
$shortcut.Description = "LAN Secure Exam Client"
$shortcut.Save()

$uninstallCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$uninstallScript`""
$regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\SecureExamClient"
New-Item -Path $regPath -Force | Out-Null
New-ItemProperty -Path $regPath -Name DisplayName -Value "Secure Exam Client" -PropertyType String -Force | Out-Null
New-ItemProperty -Path $regPath -Name DisplayVersion -Value "1.0" -PropertyType String -Force | Out-Null
New-ItemProperty -Path $regPath -Name Publisher -Value "QS Studio" -PropertyType String -Force | Out-Null
New-ItemProperty -Path $regPath -Name InstallLocation -Value $installDir -PropertyType String -Force | Out-Null
New-ItemProperty -Path $regPath -Name UninstallString -Value $uninstallCommand -PropertyType String -Force | Out-Null

Add-Type -AssemblyName PresentationFramework
[System.Windows.MessageBox]::Show(
    "Secure Exam Client installed.`n`nServer: $backend`n`nUse the Desktop shortcut to start the exam.",
    "Secure Exam Client Setup"
)
