@echo off
rem First-time setup: create test-server\ and download the Folia 1.21.11 server jar.
rem All logic lives in tools\cerb.ps1 (see RESTART_GUIDE.md).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\cerb.ps1" setup
echo.
pause
