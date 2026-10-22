#!/usr/bin/env bash
# Produce the README screenshots in docs/images/.
#
#   scripts/capture-screenshots.sh              # start stack, seed, capture, stop
#   scripts/capture-screenshots.sh --no-start   # stack already running; leave it running
#
# Flow (docs/DESIGN.md section 6): docker compose up -> wait for health ->
# seed-demo.sh -> e2e Playwright screenshot project -> docker compose down.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_URL="${API_URL:-http://localhost:8080}"
UI_URL="${UI_URL:-http://localhost:3000}"
START=1
for arg in "$@"; do
  case "$arg" in
    --no-start) START=0 ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

STARTED=0
cleanup() {
  if [[ "$STARTED" -eq 1 ]]; then
    echo
    echo "==> Stopping the stack (docker compose down)"
    (cd "$ROOT" && docker compose down) || true
  fi
}
trap cleanup EXIT

if [[ "$START" -eq 1 ]]; then
  echo "==> Starting the stack (docker compose up -d --build)"
  (cd "$ROOT" && docker compose up -d --build)
  STARTED=1
fi

echo "==> Waiting for the API and the UI"
"$ROOT/scripts/wait-for-health.sh" "$API_URL" "${WAIT_TIMEOUT:-180}"
ui_deadline=$(( $(date +%s) + 60 ))
until curl -fsS -m 3 -o /dev/null "$UI_URL"; do
  if (( $(date +%s) >= ui_deadline )); then echo "UI not reachable at $UI_URL" >&2; exit 1; fi
  sleep 2
done
echo "UI reachable at $UI_URL"

echo "==> Seeding demo data"
API_URL="$API_URL" "$ROOT/scripts/seed-demo.sh"

echo "==> Capturing screenshots with Playwright (e2e/)"
cd "$ROOT/e2e" || { echo "e2e/ directory not found" >&2; exit 1; }
npm ci --silent
PLAYWRIGHT_BASE_URL="$UI_URL" API_URL="$API_URL" npm run screenshots

echo "==> Images produced in docs/images/"
cd "$ROOT" || exit 1
found=0
for png in docs/images/*.png; do
  [[ -e "$png" ]] || continue
  found=1
  size=$(wc -c < "$png" | tr -d ' ')
  printf '    %-40s %8s bytes\n' "$png" "$size"
done
if [[ "$found" -eq 0 ]]; then
  echo "    no PNG files found in docs/images/" >&2
  exit 1
fi
