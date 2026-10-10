param([Parameter(Mandatory=$true)][int]$ExpectedPid)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $root 'build\libs\exam-platform-backend-0.0.1-SNAPSHOT.jar'
$staged = Join-Path $root 'build\staged\exam-platform-backend-0.0.1-SNAPSHOT.jar'
$java = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin\java.exe'
$existing = Get-CimInstance Win32_Process -Filter "ProcessId=$ExpectedPid"
if (-not $existing -or $existing.CommandLine -notlike "*-jar $jar*") { throw 'The running backend does not match the verified process.' }
if (-not $env:DB_PASSWORD) { throw 'Database configuration is missing; the running server has not been stopped.' }
if (-not (Test-Path -LiteralPath $staged)) { throw 'Tested staged build is missing.' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDir = Join-Path $root 'build\backups'
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
$backup = Join-Path $backupDir "backend-before-correction-$stamp.jar"
Copy-Item -LiteralPath $jar -Destination $backup
function Start-Backend {
    param([string]$Suffix)
    $outLog = Join-Path $root "server-$stamp-$Suffix.log"
    $errLog = Join-Path $root "server-$stamp-$Suffix.err.log"
    $process = Start-Process -FilePath $java -ArgumentList @('-jar',$jar) -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
    Set-Content -LiteralPath (Join-Path $root 'exam-platform.pid') -Value $process.Id
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Seconds 2
        $process.Refresh()
        if ($process.HasExited) { throw "Backend exited. See $errLog and $outLog" }
        try {
            $response = Invoke-WebRequest -Uri 'http://127.0.0.1:8081/api/public/exams' -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return $process.Id }
        } catch { }
    }
    Stop-Process -Id $process.Id -Force
    throw 'Backend startup timed out.'
}
Stop-Process -Id $ExpectedPid
Wait-Process -Id $ExpectedPid -ErrorAction SilentlyContinue
try {
    Copy-Item -LiteralPath $staged -Destination $jar -Force
    $newPid = Start-Backend 'updated'
    Write-Output "Updated backend is healthy on port 8081. PID: $newPid. Previous build: $backup"
} catch {
    $failure = $_.Exception.Message
    Copy-Item -LiteralPath $backup -Destination $jar -Force
    $restoredPid = Start-Backend 'restored'
    throw "Update failed: $failure. Previous build restored, PID $restoredPid."
}
