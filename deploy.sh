#!/bin/bash
set -e

# ── Create directories if missing ──
mkdir -p test-server/plugins

# ── Build ──
echo "Building Cerberus plugin..."
./mvnw package -q

if [ ! -f target/Cerberus.jar ]; then
    echo "ERROR: Build succeeded but target/Cerberus.jar not found."
    exit 1
fi

# ── Deploy ──
cp target/Cerberus.jar test-server/plugins/Cerberus.jar
echo "Deployed! Stop and restart the server to load changes."
