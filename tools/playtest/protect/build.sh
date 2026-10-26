#!/bin/bash
# Builds CerbTestProtect.jar (a sandbox-only stand-in for a protection plugin) against the Paper API Cerberus uses.
# Usage: bash tools/playtest/protect/build.sh [output-dir]   (default: this folder). Copy the jar into the sandbox
# server's plugins/ folder only, never into a real server.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${1:-$HERE}
# The Paper API and its libraries from the local Maven repository (a build of Cerberus downloads them).
CP=$(find "${M2:-$HOME/.m2/repository}" \( -path "*io/papermc/paper/paper-api*" -o -path "*net/kyori*" -o -path "*net/md-5*" \
      -o -path "*com/google/guava*" -o -path "*org/jetbrains/annotations*" -o -path "*org/jspecify*" \) -name "*.jar" ! -name "*sources*" | tr '\n' ':')
TMP=$(mktemp -d)
javac -nowarn --release 21 -cp "$CP" -d "$TMP" "$HERE/src/cerbtest/Protect.java"
cp "$HERE/plugin.yml" "$TMP/"
(cd "$TMP" && jar cf "$OUT/CerbTestProtect.jar" .)
rm -rf "$TMP"
echo "$OUT/CerbTestProtect.jar"
