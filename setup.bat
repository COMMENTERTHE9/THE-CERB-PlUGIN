@echo off
setlocal

:: ── Idempotent: skip if already downloaded ──
if exist "test-server\paper-1.21.1.jar" (
    echo Paper 1.21.1 already downloaded. Nothing to do.
    exit /b 0
)

:: ── Create directories ──
if not exist "test-server\plugins" mkdir "test-server\plugins"

:: ── Download latest Paper 1.21.1 build ──
echo Fetching latest Paper 1.21.1 build number...
for /f %%i in ('powershell -NoProfile -Command "(Invoke-RestMethod 'https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds').builds[-1].build"') do set BUILD=%%i

if "%BUILD%"=="" (
    echo ERROR: Could not determine latest Paper build. Check your internet connection.
    pause
    exit /b 1
)

echo Downloading Paper 1.21.1 build %BUILD%...
powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/%BUILD%/downloads/paper-1.21.1-%BUILD%.jar' -OutFile 'test-server\paper-1.21.1.jar'"

if not exist "test-server\paper-1.21.1.jar" (
    echo ERROR: Download failed.
    pause
    exit /b 1
)

echo Paper 1.21.1 build %BUILD% downloaded to test-server\paper-1.21.1.jar
echo Setup complete! Run run.bat to build and start the server.
pause
