@echo off
setlocal
cd /d "%~dp0"

set "DEV_PID_FILE=%cd%\exam-platform-dev.pid"

echo Stopping old dev instance if present...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$pidFile='%DEV_PID_FILE%';" ^
  "if (Test-Path $pidFile) { $storedPid=Get-Content $pidFile -ErrorAction SilentlyContinue; if ($storedPid) { Stop-Process -Id $storedPid -Force -ErrorAction SilentlyContinue }; Remove-Item $pidFile -Force -ErrorAction SilentlyContinue };" ^
  "Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(cmd|java|javaw)\.exe$' -and $_.CommandLine -like '*gradlew.bat bootRun*' -and $_.CommandLine -like '*exam-platform-backend*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"

echo Starting exam platform in DEV mode...
echo This mode is for local editing. Refresh browser after HTML/CSS/JS changes.
echo Java changes should restart faster than full jar rebuild mode.

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$workdir='%cd%';" ^
  "$pidFile='%DEV_PID_FILE%';" ^
  "$psi = New-Object System.Diagnostics.ProcessStartInfo;" ^
  "$psi.FileName = 'cmd.exe';" ^
  "$psi.WorkingDirectory = $workdir;" ^
  "$master=[Environment]::GetEnvironmentVariable('MASTER_ADMIN_EMAIL','Process'); if (-not $master) { $master=[Environment]::GetEnvironmentVariable('MASTER_ADMIN_EMAIL','User') }; if (-not $master) { $master='shashwatdogra1@gmail.com' };" ^
  "$db=[Environment]::GetEnvironmentVariable('DB_PASSWORD','Process'); if (-not $db) { $db=[Environment]::GetEnvironmentVariable('DB_PASSWORD','User') }; if (-not $db) { throw 'Set DB_PASSWORD before starting the development server.' }; $psi.EnvironmentVariables['DB_PASSWORD']=$db; $psi.EnvironmentVariables['MASTER_ADMIN_EMAIL']=$master;" ^
  "$mailUser=[Environment]::GetEnvironmentVariable('MAIL_USERNAME','User'); $mailPassword=[Environment]::GetEnvironmentVariable('MAIL_PASSWORD','User'); if ($mailUser) { $psi.EnvironmentVariables['MAIL_USERNAME']=$mailUser }; if ($mailPassword) { $psi.EnvironmentVariables['MAIL_PASSWORD']=$mailPassword }; $psi.Arguments = '/c gradlew.bat bootRun --no-daemon > dev-server.log 2> dev-server.err.log'; $psi.CreateNoWindow=$true;" ^
  "$psi.UseShellExecute = $false;" ^
  "$proc = [System.Diagnostics.Process]::Start($psi);" ^
  "Set-Content -Path $pidFile -Value $proc.Id"

if errorlevel 1 (
  echo Could not start dev mode.
  pause
  exit /b 1
)

timeout /t 10 /nobreak >nul

echo.
echo Exam platform DEV mode started.
for /f "tokens=2 delims=:" %%A in ('ipconfig ^| findstr /R /C:"IPv4 Address"') do (
  set "LAN_IP=%%A"
  goto :gotip
)
:gotip
set "LAN_IP=%LAN_IP: =%"
if "%LAN_IP%"=="" set "LAN_IP=192.168.31.154"
echo Public portal:  http://localhost:8081/
echo Admin portal:   http://localhost:8081/console/8f3a91d6c42e7b5a0d19/admin
echo Master admin:   http://localhost:8081/console/c7e42a9d51f8603b7a24/master
echo Master setup:   http://localhost:8081/console/setup/51b7d893e4a26c0f/master
echo LAN public:     http://%LAN_IP%:8081/
echo LAN admin:      http://%LAN_IP%:8081/console/8f3a91d6c42e7b5a0d19/admin
echo Log file:       %cd%\dev-server.log
echo Error log:      %cd%\dev-server.err.log
echo PID file:       %DEV_PID_FILE%
echo.

start "" "http://localhost:8081/"
start "" "http://localhost:8081/console/8f3a91d6c42e7b5a0d19/admin"
if "%EXAM_PLATFORM_PAUSE%"=="1" pause
