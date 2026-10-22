#!/usr/bin/env bash
# Wait until the Payment Orchestration Service reports healthy.
#
#   scripts/wait-for-health.sh [BASE_URL] [TIMEOUT_SECONDS]
#
# Polls GET {BASE_URL}/api/v1/system/health for "status":"UP" and falls back to
# GET {BASE_URL}/actuator/health/readiness. Prints a dot per attempt and exits
# non-zero when the timeout is reached.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
TIMEOUT="${2:-120}"
INTERVAL="${WAIT_INTERVAL:-2}"
BASE_URL="${BASE_URL%/}"

have_jq=0
if command -v jq >/dev/null 2>&1; then have_jq=1; fi

system_health_up() {
  local body
  body="$(curl -fsS -m 3 "${BASE_URL}/api/v1/system/health" 2>/dev/null || true)"
  [[ -z "$body" ]] && return 1
  if [[ "$have_jq" -eq 1 ]]; then
    [[ "$(printf '%s' "$body" | jq -r '.status // empty' 2>/dev/null)" == "UP" ]]
  else
    # Without jq fall back to a top-level pattern match (components also carry a status,
    # so this is less strict than the jq path).
    printf '%s' "$body" | tr -d ' \n' | grep -q '^{"status":"UP"'
  fi
}

readiness_up() {
  local body
  body="$(curl -fsS -m 3 "${BASE_URL}/actuator/health/readiness" 2>/dev/null || true)"
  [[ -n "$body" ]] && printf '%s' "$body" | tr -d ' \n' | grep -q '"status":"UP"'
}

printf 'Waiting for %s (timeout %ss) ' "$BASE_URL" "$TIMEOUT"
start=$(date +%s)
while true; do
  if system_health_up; then
    printf '\nHealthy: %s/api/v1/system/health reports UP (%ss)\n' "$BASE_URL" "$(( $(date +%s) - start ))"
    exit 0
  fi
  if readiness_up; then
    printf '\nHealthy: %s/actuator/health/readiness reports UP (%ss)\n' "$BASE_URL" "$(( $(date +%s) - start ))"
    exit 0
  fi
  if (( $(date +%s) - start >= TIMEOUT )); then
    printf '\nTimed out after %ss waiting for %s\n' "$TIMEOUT" "$BASE_URL" >&2
    exit 1
  fi
  printf '.'
  sleep "$INTERVAL"
done
