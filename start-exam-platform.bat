@echo off
setlocal
cd /d "%~dp0"

set "APP_JAR=%cd%\build\libs\exam-platform-backend-0.0.1-SNAPSHOT.jar"
set "JAVA_EXE=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin\java.exe"
set "PID_FILE=%cd%\exam-platform.pid"

if not exist "%JAVA_EXE%" (
  echo Java not found at:
  echo %JAVA_EXE%
  echo.
  echo Update JAVA_EXE inside start-exam-platform.bat if your JDK path changed.
  pause
  exit /b 1
)

echo Building latest backend jar...
call gradlew.bat bootJar -x test --no-daemon
if errorlevel 1 (
  echo Build failed.
  pause
  exit /b 1
)

echo Stopping old exam platform instance if present...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$pidFile='%PID_FILE%';" ^
  "if (Test-Path $pidFile) { $storedPid=Get-Content $pidFile -ErrorAction SilentlyContinue; if ($storedPid) { Stop-Process -Id $storedPid -Force -ErrorAction SilentlyContinue }; Remove-Item $pidFile -Force -ErrorAction SilentlyContinue };" ^
  "$jar='%APP_JAR%';" ^
  "Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -like ('*' + $jar + '*') } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"

echo Starting exam platform backend...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$jar='%APP_JAR%';" ^
  "$java='%JAVA_EXE%';" ^
  "$workdir='%cd%';" ^
  "$pidFile='%PID_FILE%';" ^
  "$db=[Environment]::GetEnvironmentVariable('DB_PASSWORD','Process'); if (-not $db) { $db=[Environment]::GetEnvironmentVariable('DB_PASSWORD','User') }; if (-not $db) { $db=[Environment]::GetEnvironmentVariable('DB_PASSWORD','Machine') }; if (-not $db) { $secure=Read-Host 'PostgreSQL DB_PASSWORD' -AsSecureString; $db=[Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)) }; if (-not $db) { throw 'DB_PASSWORD is required before starting the backend.' }; $env:DB_PASSWORD=$db;" ^
  "$master=[Environment]::GetEnvironmentVariable('MASTER_ADMIN_EMAIL','Process'); if (-not $master) { $master=[Environment]::GetEnvironmentVariable('MASTER_ADMIN_EMAIL','User') }; if (-not $master) { $master='shashwatdogra1@gmail.com' }; $env:MASTER_ADMIN_EMAIL=$master;" ^
  "$mailUser=[Environment]::GetEnvironmentVariable('MAIL_USERNAME','Process'); if (-not $mailUser) { $mailUser=[Environment]::GetEnvironmentVariable('MAIL_USERNAME','User') }; if ($mailUser) { $env:MAIL_USERNAME=$mailUser };" ^
  "$mail=[Environment]::GetEnvironmentVariable('MAIL_PASSWORD','User'); if ($mail) { $env:MAIL_PASSWORD=$mail };" ^
  "$proc=Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $workdir -RedirectStandardOutput 'server.log' -RedirectStandardError 'server.err.log' -PassThru;" ^
  "Set-Content -Path $pidFile -Value $proc.Id"

if errorlevel 1 (
  echo Could not start the backend.
  echo Check server.err.log for details.
  pause
  exit /b 1
)

timeout /t 8 /nobreak >nul

echo.
echo Exam platform started.
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
echo Logs:           %cd%\server.log
echo Errors:         %cd%\server.err.log
echo PID file:       %PID_FILE%
echo.

start "" "http://localhost:8081/"
start "" "http://localhost:8081/console/8f3a91d6c42e7b5a0d19/admin"
if "%EXAM_PLATFORM_PAUSE%"=="1" pause
