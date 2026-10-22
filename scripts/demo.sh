#!/usr/bin/env bash
# Scripted walkthrough of the Payment Orchestration Service.
#
#   scripts/demo.sh              # docker compose up -d --build, wait, run the scenario
#   scripts/demo.sh --no-start   # assume the stack (or a local backend) is already running
#   scripts/demo.sh --stop       # tear the stack down at the end (docker compose down)
#
# Every step is checked; the script exits non-zero if any step fails.
# Contract: docs/DESIGN.md sections 4.4, 4.5, 4.7 and 6.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_URL="${API_URL:-http://localhost:8080}"
API_URL="${API_URL%/}"
API="${API_URL}/api/v1"
START=1
STOP=0
for arg in "$@"; do
  case "$arg" in
    --no-start) START=0 ;;
    --stop) STOP=1 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required (https://jqlang.github.io/jq/)" >&2; exit 1; }

TMP="$(mktemp -d)"
RUN_ID="$(date -u +%Y%m%d%H%M%S)-$$"
# shellcheck disable=SC2317,SC2329  # invoked via trap
cleanup() {
  rm -rf "$TMP"
  if [[ "$STOP" -eq 1 ]]; then
    echo
    echo "==> Stopping the stack (docker compose down)"
    (cd "$ROOT" && docker compose down) || true
  fi
}
trap cleanup EXIT

section() { printf '\n==> %s\n' "$*"; }
fail() { echo "FAILED: $*" >&2; exit 1; }

# request METHOD PATH OUTFILE [curl args...] -> prints HTTP status, writes body to OUTFILE
request() {
  local method="$1" path="$2" out="$3"
  shift 3
  curl -sS -m 15 -o "$out" -w '%{http_code}' -X "$method" "${API}${path}" -H 'Accept: application/json' "$@"
}

payment_body() {
  # payment_body AMOUNT CURRENCY REFERENCE
  printf '{"payerAccount":"GB12PAYLAB00000001","payeeAccount":"GB12PAYLAB00000002","amount":"%s","currency":"%s","reference":"%s"}' "$1" "$2" "$3"
}

# wait_terminal ID TIMEOUT -> prints each status change; sets LAST_STATUS
wait_terminal() {
  local id="$1" timeout="$2" start last="" status retries
  start=$(date +%s)
  while true; do
    curl -fsS -m 5 "${API}/payments/${id}" -o "$TMP/poll.json"
    status="$(jq -r '.status' "$TMP/poll.json")"
    retries="$(jq -r '.retryCount' "$TMP/poll.json")"
    if [[ "$status" != "$last" ]]; then
      printf '    %s  status=%-13s retryCount=%s nextRetryAt=%s\n' "$(date -u +%H:%M:%S)" "$status" "$retries" "$(jq -r '.nextRetryAt // "-"' "$TMP/poll.json")"
      last="$status"
    fi
    case "$status" in
      COMPLETED|FAILED|REJECTED|CANCELLED) LAST_STATUS="$status"; return 0 ;;
    esac
    if (( $(date +%s) - start >= timeout )); then
      LAST_STATUS="$status"
      return 1
    fi
    sleep 1
  done
}

# ------------------------------------------------------------------ 0. stack
if [[ "$START" -eq 1 ]]; then
  section "Starting the stack (docker compose up -d --build)"
  (cd "$ROOT" && docker compose up -d --build) || fail "docker compose up failed"
fi

section "Waiting for the API at $API_URL"
"$ROOT/scripts/wait-for-health.sh" "$API_URL" "${WAIT_TIMEOUT:-180}" || fail "service did not become healthy"

# ------------------------------------------------------------ 1. create
section "1. Create a payment (POST /api/v1/payments)"
KEY="demo-${RUN_ID}-1"
echo "    Idempotency-Key: $KEY"
status="$(request POST /payments "$TMP/p1.json" -D "$TMP/p1.headers" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" --data "$(payment_body 125.50 GBP "INV-${RUN_ID}-1")")"
[[ "$status" == "201" ]] || { cat "$TMP/p1.json"; fail "expected 201, got $status"; }
P1="$(jq -r '.paymentId' "$TMP/p1.json")"
echo "    HTTP $status  Location: $(grep -i '^Location:' "$TMP/p1.headers" | tr -d '\r' | cut -d' ' -f2-)"
jq '{paymentId, status, amount, currency, reference, idempotencyKey, correlationId}' "$TMP/p1.json" | sed 's/^/    /'

# ------------------------------------------------------------ 2. replay
section "2. Replay the same request with the same Idempotency-Key"
status="$(request POST /payments "$TMP/p1b.json" -D "$TMP/p1b.headers" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" --data "$(payment_body 125.50 GBP "INV-${RUN_ID}-1")")"
[[ "$status" == "201" ]] || { cat "$TMP/p1b.json"; fail "expected 201 on replay, got $status"; }
replayed="$(grep -i '^Idempotent-Replayed:' "$TMP/p1b.headers" | tr -d '\r' || true)"
[[ -n "$replayed" ]] || fail "Idempotent-Replayed header missing on replay"
[[ "$(jq -r '.paymentId' "$TMP/p1b.json")" == "$P1" ]] || fail "replay returned a different paymentId"
echo "    HTTP $status  $replayed  paymentId=$P1 (same payment, no duplicate created)"

# ------------------------------------------------------------ 3. conflict
section "3. Reuse the Idempotency-Key with a different amount (expect 409)"
status="$(request POST /payments "$TMP/p1c.json" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" --data "$(payment_body 999.00 GBP "INV-${RUN_ID}-1")")"
[[ "$status" == "409" ]] || { cat "$TMP/p1c.json"; fail "expected 409, got $status"; }
echo "    HTTP $status application/problem+json"
jq . "$TMP/p1c.json" | sed 's/^/    /'

# ------------------------------------------------------------ 4. retry
section "4. Transient failure: X-Demo-Failure: settlement-timeout (retries then completes)"
status="$(request POST /payments "$TMP/p2.json" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: demo-${RUN_ID}-timeout" -H 'X-Demo-Failure: settlement-timeout' \
  --data "$(payment_body 310.75 EUR "INV-${RUN_ID}-TIMEOUT")")"
[[ "$status" == "201" ]] || { cat "$TMP/p2.json"; fail "expected 201, got $status"; }
P2="$(jq -r '.paymentId' "$TMP/p2.json")"
echo "    paymentId=$P2  polling status changes:"
wait_terminal "$P2" "${SETTLE_TIMEOUT:-90}" || fail "payment $P2 did not reach a terminal state (last: $LAST_STATUS)"
[[ "$LAST_STATUS" == "COMPLETED" ]] || fail "expected COMPLETED after retries, got $LAST_STATUS"
echo "    -> COMPLETED after $(jq -r '.retryCount' "$TMP/poll.json") retries"

# ------------------------------------------------------------ 5. failed
section "5. Permanent failure: X-Demo-Failure: settlement-rejected (expect FAILED)"
status="$(request POST /payments "$TMP/p3.json" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: demo-${RUN_ID}-rejected" -H 'X-Demo-Failure: settlement-rejected' \
  --data "$(payment_body 7800.00 EUR "INV-${RUN_ID}-REJECTED")")"
[[ "$status" == "201" ]] || { cat "$TMP/p3.json"; fail "expected 201, got $status"; }
P3="$(jq -r '.paymentId' "$TMP/p3.json")"
echo "    paymentId=$P3  polling status changes:"
wait_terminal "$P3" "${SETTLE_TIMEOUT:-60}" || fail "payment $P3 did not reach a terminal state (last: $LAST_STATUS)"
[[ "$LAST_STATUS" == "FAILED" ]] || fail "expected FAILED, got $LAST_STATUS"
echo "    -> FAILED  failureReason: $(jq -r '.failureReason // "-"' "$TMP/poll.json")"

# ------------------------------------------------------------ 6. cancel
section "6. Cancel a payment immediately after creation (POST /payments/{id}/cancel)"
status="$(request POST /payments "$TMP/p4.json" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: demo-${RUN_ID}-cancel" --data "$(payment_body 220.40 SGD "INV-${RUN_ID}-CANCEL")")"
[[ "$status" == "201" ]] || { cat "$TMP/p4.json"; fail "expected 201, got $status"; }
P4="$(jq -r '.paymentId' "$TMP/p4.json")"
status="$(request POST "/payments/$P4/cancel" "$TMP/p4c.json" -H 'Content-Type: application/json' \
  --data '{"reason":"Demo cancellation"}')"
case "$status" in
  200) echo "    HTTP 200  paymentId=$P4 status=$(jq -r '.status' "$TMP/p4c.json")" ;;
  409) echo "    HTTP 409  the payment already reached a terminal state before the cancel arrived (a race the state machine rejects):"
       jq . "$TMP/p4c.json" | sed 's/^/    /' ;;
  *)   cat "$TMP/p4c.json"; fail "expected 200 or 409 on cancel, got $status" ;;
esac

# ------------------------------------------------------------ 7. events
section "7. Event and audit trail for the retried payment (GET /payments/{id}/events)"
curl -fsS -m 5 "${API}/payments/${P2}/events" -o "$TMP/events.json" || fail "events lookup failed"
jq -r '.events[] | "    \(.occurredAt)  \(.eventType | . + " " * (24 - length))  \(.fromStatus // "-") -> \(.toStatus // "-")"' "$TMP/events.json"
echo "    audit entries: $(jq '.audit | length' "$TMP/events.json")"

# ------------------------------------------------------------ 8. reconcile
section "8. Reconciliation run (scenario with-discrepancies, seed 42)"
status="$(request POST /reconciliation/run "$TMP/recon.json" -H 'Content-Type: application/json' \
  --data '{"scenario":"with-discrepancies","seed":42}')"
[[ "$status" == "200" ]] || { cat "$TMP/recon.json"; fail "expected 200, got $status"; }
jq '{runId, scenario, status, recordsChecked, matched, unmatched, byClassification}' "$TMP/recon.json" | sed 's/^/    /'

# ------------------------------------------------------------ 9. stats/health
section "9. System stats (GET /system/stats)"
curl -fsS -m 5 "${API}/system/stats" | jq . | sed 's/^/    /'

section "10. System health (GET /system/health)"
curl -fsS -m 5 "${API}/system/health" -o "$TMP/health.json"
jq -r '"    overall: \(.status)", (.components | to_entries[] | "    \(.key | . + " " * (12 - length)) \(.value.status)  \(.value.detail // "")")' "$TMP/health.json"

section "Demo complete"
echo "    UI: http://localhost:3000   Swagger: ${API_URL}/swagger-ui.html   Grafana: http://localhost:3001"
exit 0
