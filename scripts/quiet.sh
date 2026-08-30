#!/usr/bin/env bash
# Runs a build/test command, buffering its output.
# On success: prints a short pass line + the last few lines of output.
# On failure: prints the full output untouched, so no error is ever hidden.
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "Usage: $0 <command> [args...]" >&2
  exit 64
fi

tail_lines="${QUIET_TAIL_LINES:-20}"
log="$(mktemp)"
trap 'rm -f "$log"' EXIT

start="$(date +%s)"
set +e
"$@" >"$log" 2>&1
status=$?
set -e
duration=$(( $(date +%s) - start ))

if [ "$status" -eq 0 ]; then
  echo "PASSED: $* (${duration}s)"
  if grep -q '^BUILD SUCCESSFUL' "$log"; then
    # Gradle: the real summary starts here; everything before it (shutdown-hook
    # logging from the last test's Spring context) is noise, not signal.
    sed -n '/^BUILD SUCCESSFUL/,$p' "$log"
  else
    tail -n "$tail_lines" "$log"
  fi
else
  echo "FAILED: $* (${duration}s, exit ${status}) — full output below"
  cat "$log"
fi

exit "$status"
