#!/bin/bash
set -e

JAVA_XMS="${JAVA_XMS:-512M}"
JAVA_XMX="${JAVA_XMX:-2G}"

# ── Create directories if missing ──
mkdir -p test-server/plugins

# ── Build ──
echo "Building Cerberus plugin..."
./mvnw package -q

if [ ! -f target/Cerberus.jar ]; then
    echo "ERROR: Build succeeded but target/Cerberus.jar not found. Check pom.xml packaging config."
    exit 1
fi

# ── Deploy ──
cp target/Cerberus.jar test-server/plugins/Cerberus.jar
echo "Plugin deployed to test-server/plugins/Cerberus.jar"

# ── Check Paper JAR ──
if [ ! -f test-server/paper-1.21.1.jar ]; then
    echo "ERROR: Paper JAR not found. Run ./setup.sh first."
    exit 1
fi

# ── Start server ──
echo "Starting test server (heap: ${JAVA_XMS} - ${JAVA_XMX})..."
cd test-server
java \
    -Xms${JAVA_XMS} -Xmx${JAVA_XMX} \
    -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC \
    -XX:G1HeapRegionSize=8M -XX:G1NewSizePercent=30 \
    -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCLiveThresholdPercent=90 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -jar paper-1.21.1.jar nogui
