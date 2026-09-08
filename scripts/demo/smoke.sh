#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:4000}"
USER_NAME="${SMOKE_USER:-smoke$(date +%s)}"
USER_PASSWORD="${SMOKE_PASSWORD:-smokepass123}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

request() {
  local label="$1"
  local expected="$2"
  shift 2
  local headers="$WORK_DIR/headers"
  local body="$WORK_DIR/body"
  curl -sS -D "$headers" -o "$body" "$@"
  local actual
  actual="$(awk 'NR == 1 { print $2 }' "$headers")"
  printf '=== %s\\nSTATUS %s\\nBODY ' "$label" "$actual"
  cat "$body"
  printf '\\n'
  [[ "$actual" == "$expected" ]] || {
    echo "unexpected status for $label: expected $expected, got $actual" >&2
    exit 1
  }
  cp "$body" "$WORK_DIR/last-body"
}

request "POST /accounts/" 200 \
  -X POST "$BASE_URL/accounts/" \
  -H 'Content-Type: application/json' \
  --data "{\"username\":\"$USER_NAME\",\"password\":\"$USER_PASSWORD\"}"

request "POST /uaa/oauth/token" 200 \
  -X POST "$BASE_URL/uaa/oauth/token" \
  -u 'browser:' \
  -d scope=ui \
  --data-urlencode "username=$USER_NAME" \
  --data-urlencode "password=$USER_PASSWORD" \
  -d grant_type=password
TOKEN="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["access_token"])' "$WORK_DIR/last-body")"
AUTH_HEADER="Authorization: Bearer $TOKEN"

request "PUT /accounts/current" 200 \
  -X PUT "$BASE_URL/accounts/current" \
  -H "$AUTH_HEADER" \
  -H 'Content-Type: application/json' \
  --data '{"incomes":[{"title":"Salary","amount":1000,"currency":"USD","period":"MONTH","icon":"salary"}],"expenses":[{"title":"Tokyo","amount":147.85,"currency":"JPY","period":"MONTH","icon":"travel"}],"saving":{"amount":100,"currency":"USD","interest":1,"deposit":false,"capitalization":false},"note":"T1 smoke"}'
request "GET /accounts/current" 200 -H "$AUTH_HEADER" "$BASE_URL/accounts/current"
request "GET /statistics/current" 200 -H "$AUTH_HEADER" "$BASE_URL/statistics/current"
request "GET /notifications/recipients/current" 200 \
  -H "$AUTH_HEADER" "$BASE_URL/notifications/recipients/current"
request "PUT /notifications/recipients/current" 200 \
  -X PUT "$BASE_URL/notifications/recipients/current" \
  -H "$AUTH_HEADER" \
  -H 'Content-Type: application/json' \
  --data '{"email":"smoke@example.com","scheduledNotifications":{"REMIND":{"active":true,"frequency":"MONTHLY"}}}'
request "GET /accounts/demo" 200 "$BASE_URL/accounts/demo"
grep -q '"currency":"JPY"' "$WORK_DIR/last-body" || {
  echo "seeded demo account did not contain JPY" >&2
  exit 1
}
request "GET /statistics/demo" 200 -H "$AUTH_HEADER" "$BASE_URL/statistics/demo"
request "GET /" 200 "$BASE_URL/"
request "GET /css/style.css" 200 "$BASE_URL/css/style.css"
request "GET /js/main.js" 200 "$BASE_URL/js/main.js"

echo "Smoke test passed"
