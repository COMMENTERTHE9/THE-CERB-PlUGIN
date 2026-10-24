@echo off
rem Show Java, server folder, worlds, plugin, and whether the server is running.
rem All logic lives in tools\cerb.ps1 (see RESTART_GUIDE.md).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\cerb.ps1" status
echo.
pause
