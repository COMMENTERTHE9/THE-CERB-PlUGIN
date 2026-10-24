#!/usr/bin/env bash
# Build the plugin and copy it into test-server/plugins (server must be stopped).
# All logic lives in tools/cerb.sh (see RESTART_GUIDE.md).
exec "$(dirname "$0")/tools/cerb.sh" deploy "$@"
