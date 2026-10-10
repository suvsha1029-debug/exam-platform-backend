@echo off
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%cd%\start-examshield.ps1"

if errorlevel 1 (
  echo.
  echo ExamShield could not start. Check the message above.
  pause
  exit /b 1
)
