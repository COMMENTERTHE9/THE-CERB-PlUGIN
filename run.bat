@echo off
setlocal

if "%JAVA_XMS%"=="" set JAVA_XMS=512M
if "%JAVA_XMX%"=="" set JAVA_XMX=2G
if "%BUILD_JAVA_HOME%"=="" if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot" set BUILD_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
if "%RUN_JAVA_HOME%"=="" if exist "C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot" set RUN_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot

if not exist "%BUILD_JAVA_HOME%\bin\java.exe" (
    echo ERROR: Build JDK not found. Set BUILD_JAVA_HOME to a Java 21 JDK.
    pause
    exit /b 1
)

if not exist "%RUN_JAVA_HOME%\bin\java.exe" (
    echo ERROR: Runtime JDK not found. Set RUN_JAVA_HOME to your Folia Java 25 JDK.
    pause
    exit /b 1
)

:: ── Create directories if missing ──
if not exist "test-server\plugins" mkdir "test-server\plugins"

:: ── Build ──
echo Building Cerberus plugin...
set "ORIGINAL_JAVA_HOME=%JAVA_HOME%"
set "ORIGINAL_PATH=%PATH%"
set "JAVA_HOME=%BUILD_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call mvnw.cmd clean package -q
if errorlevel 1 (
    set "JAVA_HOME=%ORIGINAL_JAVA_HOME%"
    set "PATH=%ORIGINAL_PATH%"
    echo BUILD FAILED
    pause
    exit /b 1
)
set "JAVA_HOME=%ORIGINAL_JAVA_HOME%"
set "PATH=%ORIGINAL_PATH%"

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
"%RUN_JAVA_HOME%\bin\java.exe" ^
    -Xms%JAVA_XMS% -Xmx%JAVA_XMX% ^
    -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 ^
    -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC ^
    -XX:G1HeapRegionSize=8M -XX:G1NewSizePercent=30 ^
    -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCLiveThresholdPercent=90 ^
    -XX:+HeapDumpOnOutOfMemoryError ^
    -jar folia-1.21.11-14.jar nogui
pause
