@echo off
rem Start the Folia test server in this window without rebuilding.
rem All logic lives in tools\cerb.ps1 (see RESTART_GUIDE.md).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\cerb.ps1" start
echo.
pause
