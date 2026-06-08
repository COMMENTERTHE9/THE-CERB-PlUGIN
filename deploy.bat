@echo off
setlocal

:: ── Create directories if missing ──
if not exist "test-server\plugins" mkdir "test-server\plugins"

:: ── Build ──
echo Building Cerberus plugin...
call mvnw.cmd package -q
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)

if not exist "target\Cerberus.jar" (
    echo ERROR: Build succeeded but target\Cerberus.jar not found.
    pause
    exit /b 1
)

:: ── Deploy ──
copy /Y "target\Cerberus.jar" "test-server\plugins\Cerberus.jar" >nul
echo Deployed! Stop and restart the server to load changes.
pause
