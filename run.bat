@echo off
setlocal

if "%JAVA_XMS%"=="" set JAVA_XMS=512M
if "%JAVA_XMX%"=="" set JAVA_XMX=2G

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
    echo ERROR: Build succeeded but target\Cerberus.jar not found. Check pom.xml packaging config.
    pause
    exit /b 1
)

:: ── Deploy ──
copy /Y "target\Cerberus.jar" "test-server\plugins\Cerberus.jar" >nul
echo Plugin deployed to test-server\plugins\Cerberus.jar

:: ── Check Server JAR ──
if not exist "test-server\folia-1.21.11-14.jar" (
    echo ERROR: Folia JAR not found at test-server\folia-1.21.11-14.jar
    pause
    exit /b 1
)

:: ── Start server ──
echo Starting Folia test server (heap: %JAVA_XMS% - %JAVA_XMX%)...
cd test-server
java ^
    -Xms%JAVA_XMS% -Xmx%JAVA_XMX% ^
    -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 ^
    -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC ^
    -XX:G1HeapRegionSize=8M -XX:G1NewSizePercent=30 ^
    -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCLiveThresholdPercent=90 ^
    -XX:+HeapDumpOnOutOfMemoryError ^
    -jar folia-1.21.11-14.jar nogui
pause
