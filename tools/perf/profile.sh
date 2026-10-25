#!/usr/bin/env bash
# CPU + allocation profile of a running LOCAL test server with Java Flight Recorder (JDK 21+),
# summarised per Cerberus method by jfr_summary.py. Nothing is uploaded anywhere.
# Usage: tools/perf/profile.sh <seconds> [out-dir]   (run a load meanwhile, e.g. tools/playtest/load.js)
set -eu
SECS="${1:-60}"
OUT="${2:-./perf-out}"
HERE="$(cd "$(dirname "$0")" && pwd)"
PID="$(jcmd -l 2>/dev/null | grep -Ei 'folia|paper' | awk '{print $1}' | head -1 || true)"   # JVMs only
[ -n "$PID" ] || { echo "no running folia/paper server found"; exit 1; }
mkdir -p "$OUT"
FILE="$OUT/cerb-$(date +%Y%m%d-%H%M%S).jfr"
# JFR's "profile" settings, but sampling running threads every 1 ms instead of 10 ms.
jfr configure --input profile jdk.ExecutionSample#period=1ms --output "$OUT/fine.jfc" >/dev/null
jcmd "$PID" JFR.start name=cerb settings="$OUT/fine.jfc" duration="${SECS}s" filename="$FILE" >/dev/null
echo "recording ${SECS}s from pid $PID into $FILE"
sleep $((SECS + 3))
python3 "$HERE/jfr_summary.py" "$FILE" "$SECS" 1
