@echo off
setlocal
cd /d "%~dp0"

set "PID_FILE=%cd%\exam-platform.pid"
set "DEV_PID_FILE=%cd%\exam-platform-dev.pid"
set "APP_JAR=%cd%\build\libs\exam-platform-backend-0.0.1-SNAPSHOT.jar"

echo Stopping exam platform backend...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$pidFile='%PID_FILE%';" ^
  "$devPidFile='%DEV_PID_FILE%';" ^
  "$jar='%APP_JAR%';" ^
  "if (Test-Path $pidFile) { $storedPid=Get-Content $pidFile -ErrorAction SilentlyContinue; if ($storedPid) { Stop-Process -Id $storedPid -Force -ErrorAction SilentlyContinue }; Remove-Item $pidFile -Force -ErrorAction SilentlyContinue };" ^
  "if (Test-Path $devPidFile) { $storedDevPid=Get-Content $devPidFile -ErrorAction SilentlyContinue; if ($storedDevPid) { Stop-Process -Id $storedDevPid -Force -ErrorAction SilentlyContinue }; Remove-Item $devPidFile -Force -ErrorAction SilentlyContinue };" ^
  "Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(cmd|java|javaw)\.exe$' -and $_.CommandLine -like '*gradlew.bat bootRun*' -and $_.CommandLine -like '*exam-platform-backend*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue };" ^
  "Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -like ('*' + $jar + '*') } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"

echo Done.
if "%EXAM_PLATFORM_PAUSE%"=="1" pause
