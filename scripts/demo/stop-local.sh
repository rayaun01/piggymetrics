#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNTIME_DIR="${PIGGYMETRICS_RUNTIME_DIR:-$ROOT_DIR/.demo-runtime}"
PID_DIR="$RUNTIME_DIR/pids"
MONGO_DATA_DIR="${MONGO_DATA_DIR:-$RUNTIME_DIR/mongodb-data}"
MONGO_BIN="${MONGO_BIN:-mongod}"

if [[ "$MONGO_BIN" == */* ]]; then
  if [[ ! -x "$MONGO_BIN" ]]; then
    echo "MONGO_BIN '$MONGO_BIN' was not found or is not executable; set MONGO_BIN to the mongod binary." >&2
    exit 1
  fi
elif ! command -v "$MONGO_BIN" >/dev/null 2>&1; then
  echo "MONGO_BIN '$MONGO_BIN' was not found on PATH; set MONGO_BIN to the mongod binary." >&2
  exit 1
fi

if [[ -d "$PID_DIR" ]]; then
  for pid_file in "$PID_DIR"/*.pid; do
    [[ -f "$pid_file" ]] || continue
    pid="$(cat "$pid_file")"
    kill "$pid" 2>/dev/null || true
    rm -f "$pid_file"
  done
fi

if pgrep -f "$MONGO_BIN.*$MONGO_DATA_DIR" >/dev/null; then
  "$MONGO_BIN" --dbpath "$MONGO_DATA_DIR" --shutdown || true
fi
