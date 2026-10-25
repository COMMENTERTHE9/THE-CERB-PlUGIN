#!/usr/bin/env bash
# CPU + allocation profile of a running LOCAL test server with Java Flight Recorder (JDK 21+),
# summarised per Cerberus method by jfr_summary.py. Nothing is uploaded anywhere.
# Usage: tools/perf/profile.sh <seconds> [out-dir]   (run a load meanwhile, e.g. tools/playtest/load.js)
set -eu
SECS="${1:-60}"
OUT="${2:-./perf-out}"
HERE="$(cd "$(dirname "$0")" && pwd)"
PID="$(pgrep -f 'java.*(folia|paper).*\.jar' | head -1 || true)"
[ -n "$PID" ] || { echo "no running folia/paper server found"; exit 1; }
mkdir -p "$OUT"
FILE="$OUT/cerb-$(date +%Y%m%d-%H%M%S).jfr"
jcmd "$PID" JFR.start name=cerb settings=profile duration="${SECS}s" filename="$FILE" >/dev/null
echo "recording ${SECS}s from pid $PID into $FILE"
sleep $((SECS + 3))
python3 "$HERE/jfr_summary.py" "$FILE" "$SECS"
