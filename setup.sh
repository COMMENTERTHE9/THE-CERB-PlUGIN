#!/bin/bash
set -e

# ── Dependency checks ──
command -v curl >/dev/null 2>&1 || { echo "ERROR: curl is required but not installed."; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "ERROR: python3 is required but not installed."; exit 1; }

# ── Idempotent: skip if already downloaded ──
if [ -f test-server/paper-1.21.1.jar ]; then
    echo "Paper 1.21.1 already downloaded. Nothing to do."
    exit 0
fi

# ── Create directories ──
mkdir -p test-server/plugins

# ── Download latest Paper 1.21.1 build ──
echo "Fetching latest Paper 1.21.1 build number..."
BUILD=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds" \
    | python3 -c "import sys,json; builds=json.load(sys.stdin)['builds']; print(builds[-1]['build'])")

if [ -z "$BUILD" ]; then
    echo "ERROR: Could not determine latest Paper build. Check your internet connection."
    exit 1
fi

echo "Downloading Paper 1.21.1 build $BUILD..."
curl -o test-server/paper-1.21.1.jar \
    "https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/$BUILD/downloads/paper-1.21.1-$BUILD.jar"

if [ ! -f test-server/paper-1.21.1.jar ]; then
    echo "ERROR: Download failed."
    exit 1
fi

echo "Paper 1.21.1 build $BUILD downloaded to test-server/paper-1.21.1.jar"

# ── Set executable permissions ──
chmod +x mvnw setup.sh run.sh deploy.sh 2>/dev/null || true

echo "Setup complete! Run ./run.sh to build and start the server."
