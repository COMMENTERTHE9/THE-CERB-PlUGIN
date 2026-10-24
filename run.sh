#!/usr/bin/env bash
# Build, back up plugin data, deploy, and start the Folia test server.
# All logic lives in tools/cerb.sh (see RESTART_GUIDE.md).
exec "$(dirname "$0")/tools/cerb.sh" run "$@"
