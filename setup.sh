#!/usr/bin/env bash
# First-time setup: create test-server/ and download the Folia 1.21.11 server jar.
# All logic lives in tools/cerb.sh (see RESTART_GUIDE.md).
exec "$(dirname "$0")/tools/cerb.sh" setup "$@"
