@echo off
rem Build the plugin and copy it into test-server\plugins (server must be stopped).
rem All logic lives in tools\cerb.ps1 (see RESTART_GUIDE.md).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\cerb.ps1" deploy
echo.
pause
