#!/usr/bin/env bash
# Deterministic synthetic demo data for the Payment Orchestration Service.
#
#   scripts/seed-demo.sh            # against http://localhost:8080
#   API_URL=http://host:8080 scripts/seed-demo.sh
#
# Creates 12 payments with idempotency keys demo-seed-001..012 (so re-running is
# safe: existing payments are replayed, not duplicated), including one
# settlement-timeout retry, one settlement-rejected failure, one cancellation
# and one idempotent replay, waits for every payment to settle, then runs a
# "with-discrepancies" reconciliation. Contract: docs/DESIGN.md section 6.
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
API_URL="${API_URL%/}"
API="${API_URL}/api/v1"
SETTLE_TIMEOUT="${SETTLE_TIMEOUT:-60}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# ---------------------------------------------------------------- JSON helpers
# jq is preferred. If it is missing we fall back to python3 (3.12 preferred).
PYTHON=""
if ! command -v jq >/dev/null 2>&1; then
  for candidate in "$HOME/.local/bin/python3.12" python3.12 python3; do
    if command -v "$candidate" >/dev/null 2>&1; then PYTHON="$candidate"; break; fi
  done
  if [[ -z "$PYTHON" ]]; then
    echo "seed-demo.sh needs jq (preferred) or python3 for JSON parsing" >&2
    exit 1
  fi
  echo "note: jq not found, using $PYTHON for JSON parsing"
fi

# json_get FILE KEY  -> prints top-level string/number field or empty
json_get() {
  if [[ -z "$PYTHON" ]]; then
    jq -r --arg k "$2" '.[$k] // empty' "$1"
  else
    "$PYTHON" -I -c 'import json,sys
d=json.load(open(sys.argv[1])); v=d.get(sys.argv[2])
print("" if v is None else v)' "$1" "$2"
  fi
}

# pending_count FILE -> number of payments in a non-terminal state from /system/stats
pending_count() {
  if [[ -z "$PYTHON" ]]; then
    jq -r '[.payments.byStatus // {} | .RECEIVED, .VALIDATED, .PROCESSING, .RETRY_PENDING] | map(. // 0) | add' "$1"
  else
    "$PYTHON" -I -c 'import json,sys
s=json.load(open(sys.argv[1])).get("payments",{}).get("byStatus",{}) or {}
print(sum(int(s.get(k) or 0) for k in ("RECEIVED","VALIDATED","PROCESSING","RETRY_PENDING")))' "$1"
  fi
}

pretty() {
  if [[ -z "$PYTHON" ]]; then jq . "$1"; else "$PYTHON" -I -m json.tool "$1"; fi
}

# ---------------------------------------------------------------- HTTP helpers
# request METHOD PATH OUTFILE [curl args...] -> prints HTTP status, body in OUTFILE
request() {
  local method="$1" path="$2" out="$3"
  shift 3
  curl -sS -m 15 -o "$out" -w '%{http_code}' -X "$method" "${API}${path}" \
    -H 'Accept: application/json' "$@"
}

# create_payment N KEY REF PAYER PAYEE AMOUNT CURRENCY [FAILURE_MODE]
create_payment() {
  local n="$1" key="$2" ref="$3" payer="$4" payee="$5" amount="$6" currency="$7" failure="${8:-}"
  local body="$TMP/create-$n.json" headers="$TMP/create-$n.headers" status replayed="" id pstatus
  local -a extra=()
  if [[ -n "$failure" ]]; then extra+=(-H "X-Demo-Failure: $failure"); fi
  status="$(request POST /payments "$body" -D "$headers" \
    -H 'Content-Type: application/json' -H "Idempotency-Key: $key" ${extra[@]+"${extra[@]}"} \
    --data "{\"payerAccount\":\"$payer\",\"payeeAccount\":\"$payee\",\"amount\":\"$amount\",\"currency\":\"$currency\",\"reference\":\"$ref\"}")"
  if [[ "$status" != "201" ]]; then
    echo "  [$n] $ref -> HTTP $status" >&2
    pretty "$body" >&2 || cat "$body" >&2
    return 1
  fi
  if grep -qi '^Idempotent-Replayed: *true' "$headers"; then replayed=" (replayed)"; fi
  id="$(json_get "$body" paymentId)"
  pstatus="$(json_get "$body" status)"
  printf '  [%s] %-14s %-3s %10s  %-18s -> 201 %-9s %s%s\n' "$n" "$ref" "$currency" "$amount" "${failure:-}" "$pstatus" "$id" "$replayed"
  printf '%s' "$id" > "$TMP/id-$n"
}

echo "==> Seeding demo data into $API_URL"
curl -fsS -m 5 "${API}/system/health" -o "$TMP/health.json" || { echo "API not reachable at $API_URL (run scripts/wait-for-health.sh first)" >&2; exit 1; }

echo "==> Creating 12 payments (idempotency keys demo-seed-001..012)"
#               n    key            reference      payer               payee               amount     ccy  failure mode
create_payment 001 demo-seed-001 INV-DEMO-0001 GB12PAYLAB00000001 GB12PAYLAB00000002    125.50 GBP
create_payment 002 demo-seed-002 INV-DEMO-0002 GB12PAYLAB00000003 GB12PAYLAB00000004   2400.00 GBP
create_payment 003 demo-seed-003 INV-DEMO-0003 DE89PAYLAB00000005 DE89PAYLAB00000006    310.75 EUR settlement-timeout
create_payment 004 demo-seed-004 INV-DEMO-0004 US10PAYLAB00000007 US10PAYLAB00000008    999.99 USD
create_payment 005 demo-seed-005 INV-DEMO-0005 SG56PAYLAB00000009 SG56PAYLAB00000010   1500.00 SGD
create_payment 006 demo-seed-006 INV-DEMO-0006 GB12PAYLAB00000011 GB12PAYLAB00000012     42.10 GBP
create_payment 007 demo-seed-007 INV-DEMO-0007 DE89PAYLAB00000013 DE89PAYLAB00000014   7800.00 EUR settlement-rejected
create_payment 008 demo-seed-008 INV-DEMO-0008 US10PAYLAB00000015 US10PAYLAB00000016     65.00 USD
create_payment 009 demo-seed-009 INV-DEMO-0009 SG56PAYLAB00000017 SG56PAYLAB00000018    220.40 SGD

# Payment 9 is cancelled straight after creation (allowed from RECEIVED/VALIDATED/RETRY_PENDING).
id9="$(cat "$TMP/id-009")"
cstatus="$(request POST "/payments/$id9/cancel" "$TMP/cancel-009.json" -H 'Content-Type: application/json' \
  --data '{"reason":"Customer request (demo seed)"}')"
case "$cstatus" in
  200) echo "  [009] cancelled -> $(json_get "$TMP/cancel-009.json" status)" ;;
  409) echo "  [009] cancel returned 409 (payment already reached a terminal state: $(json_get "$TMP/cancel-009.json" detail)); continuing" ;;
  *)   echo "  [009] cancel returned HTTP $cstatus" >&2; cat "$TMP/cancel-009.json" >&2; exit 1 ;;
esac

create_payment 010 demo-seed-010 INV-DEMO-0010 GB12PAYLAB00000019 GB12PAYLAB00000020  18250.00 GBP
create_payment 011 demo-seed-011 INV-DEMO-0011 DE89PAYLAB00000021 DE89PAYLAB00000022    530.25 EUR
# Payment 11 is submitted a second time with the same key and body: an idempotent replay.
create_payment 011 demo-seed-011 INV-DEMO-0011 DE89PAYLAB00000021 DE89PAYLAB00000022    530.25 EUR
create_payment 012 demo-seed-012 INV-DEMO-0012 US10PAYLAB00000023 US10PAYLAB00000024   3100.00 USD

echo "==> Waiting for all payments to reach a terminal state (max ${SETTLE_TIMEOUT}s)"
start=$(date +%s)
while true; do
  curl -fsS -m 5 "${API}/system/stats" -o "$TMP/stats.json"
  pending="$(pending_count "$TMP/stats.json")"
  if [[ "${pending:-0}" == "0" ]]; then
    printf '\n  all payments settled after %ss\n' "$(( $(date +%s) - start ))"
    break
  fi
  if (( $(date +%s) - start >= SETTLE_TIMEOUT )); then
    printf '\n  %s payment(s) still in flight after %ss; continuing anyway\n' "$pending" "$SETTLE_TIMEOUT"
    break
  fi
  printf '.'
  sleep 2
done

echo "==> Running reconciliation (scenario with-discrepancies, seed 42)"
rstatus="$(request POST /reconciliation/run "$TMP/recon.json" -H 'Content-Type: application/json' \
  --data '{"scenario":"with-discrepancies","seed":42}')"
if [[ "$rstatus" != "200" ]]; then
  echo "  reconciliation returned HTTP $rstatus" >&2
  cat "$TMP/recon.json" >&2
  exit 1
fi
echo "  runId=$(json_get "$TMP/recon.json" runId) status=$(json_get "$TMP/recon.json" status) checked=$(json_get "$TMP/recon.json" recordsChecked) matched=$(json_get "$TMP/recon.json" matched) unmatched=$(json_get "$TMP/recon.json" unmatched)"

echo "==> Final stats"
curl -fsS -m 5 "${API}/system/stats" -o "$TMP/stats.json"
pretty "$TMP/stats.json"
echo "==> Seed complete"
