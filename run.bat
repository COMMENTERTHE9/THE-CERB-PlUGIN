@echo off
rem Build the plugin, back up plugin data, deploy, and start the Folia test server in this window.
rem All logic lives in tools\cerb.ps1 (see RESTART_GUIDE.md).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\cerb.ps1" run
echo.
pause
