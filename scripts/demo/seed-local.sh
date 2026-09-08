#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MONGO_BIN="${MONGO_BIN:-$ROOT_DIR/../piggymetrics-demo-harness/mongodb/bin/mongo}"
MONGODB_PASSWORD="${MONGODB_PASSWORD:-password}"
MONGO_AUTH="${MONGO_AUTH:-1}"
SEED_FILE="$(mktemp)"
trap 'rm -f "$SEED_FILE"' EXIT

sed \
  -e "s#__MONGODB_PASSWORD__#${MONGODB_PASSWORD//\\/\\\\}#" \
  -e "s#__ACCOUNT_DUMP__#$ROOT_DIR/mongodb/dump/account-service-dump.js#" \
  "$ROOT_DIR/scripts/demo/seed-local.js" >"$SEED_FILE"

if [[ "$MONGO_AUTH" == "1" ]]; then
  "$MONGO_BIN" piggymetrics_auth -u user -p "$MONGODB_PASSWORD" \
    --authenticationDatabase piggymetrics_auth "$SEED_FILE"
else
  "$MONGO_BIN" admin "$SEED_FILE"
fi
