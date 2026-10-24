@echo off
rem Copy worlds and plugin data into test-server\backups (server must be stopped).
rem All logic lives in tools\cerb.ps1 (see RESTART_GUIDE.md).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\cerb.ps1" backup
echo.
pause
