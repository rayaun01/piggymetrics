#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MONGO_BIN="${MONGO_BIN:-mongo}"
MONGODB_PASSWORD="${MONGODB_PASSWORD:-password}"
MONGO_AUTH="${MONGO_AUTH:-1}"
SEED_FILE="$(mktemp)"
PASSWORD_JSON="$(printf '%s' "$MONGODB_PASSWORD" | python3 -c 'import json, sys; print(json.dumps(sys.stdin.read()))')"
trap 'rm -f "$SEED_FILE"' EXIT

if [[ "$MONGO_BIN" == */* ]]; then
  if [[ ! -x "$MONGO_BIN" ]]; then
    echo "MONGO_BIN '$MONGO_BIN' was not found or is not executable; set MONGO_BIN to the Mongo shell binary." >&2
    exit 1
  fi
elif ! command -v "$MONGO_BIN" >/dev/null 2>&1; then
  echo "MONGO_BIN '$MONGO_BIN' was not found on PATH; set MONGO_BIN to the Mongo shell binary." >&2
  exit 1
fi

sed \
  -e "s#__ACCOUNT_DUMP__#$ROOT_DIR/mongodb/dump/account-service-dump.js#" \
  "$ROOT_DIR/scripts/demo/seed-local.js" >"$SEED_FILE"

if [[ "$MONGO_AUTH" == "1" ]]; then
  "$MONGO_BIN" piggymetrics_auth -u user -p "$MONGODB_PASSWORD" \
    --authenticationDatabase piggymetrics_auth \
    --eval "var seedPassword = ${PASSWORD_JSON};" "$SEED_FILE"
else
  "$MONGO_BIN" admin \
    --eval "var seedPassword = ${PASSWORD_JSON};" "$SEED_FILE"
fi
