#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNTIME_DIR="${PIGGYMETRICS_RUNTIME_DIR:-$ROOT_DIR/.demo-runtime}"
LOG_DIR="$RUNTIME_DIR/logs"
PID_DIR="$RUNTIME_DIR/pids"
MONGO_BIN="${MONGO_BIN:-mongod}"
MONGO_DATA_DIR="${MONGO_DATA_DIR:-$RUNTIME_DIR/mongodb-data}"
MONGODB_PASSWORD="${MONGODB_PASSWORD:-password}"
JAVA_BIN="${JAVA_BIN:-$HOME/.local/jdks/jdk8u504-b01/bin/java}"

if [[ "$MONGO_BIN" == */* ]]; then
  if [[ ! -x "$MONGO_BIN" ]]; then
    echo "MONGO_BIN '$MONGO_BIN' was not found or is not executable; set MONGO_BIN to the mongod binary." >&2
    exit 1
  fi
elif ! command -v "$MONGO_BIN" >/dev/null 2>&1; then
  echo "MONGO_BIN '$MONGO_BIN' was not found on PATH; set MONGO_BIN to the mongod binary." >&2
  exit 1
fi

if [[ "$MONGO_BIN" == */mongod ]]; then
  MONGO_SHELL_BIN="${MONGO_BIN%/mongod}/mongo"
elif [[ "$MONGO_BIN" == "mongod" ]]; then
  MONGO_SHELL_BIN=mongo
else
  MONGO_SHELL_BIN="${MONGO_SHELL_BIN:-mongo}"
fi

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT_DIR/.env"
  set +a
fi

mkdir -p "$LOG_DIR" "$PID_DIR" "$MONGO_DATA_DIR"

if ! pgrep -f "$ROOT_DIR/scripts/demo/rates-stub.py" >/dev/null; then
  nohup python3 "$ROOT_DIR/scripts/demo/rates-stub.py" \
    >"$LOG_DIR/rates-stub.log" 2>&1 &
  echo "$!" >"$PID_DIR/rates-stub.pid"
fi

if ! pgrep -f "$MONGO_BIN.*$MONGO_DATA_DIR" >/dev/null; then
  "$MONGO_BIN" --dbpath "$MONGO_DATA_DIR" --bind_ip 127.0.0.1 \
    --port 27017 --logpath "$LOG_DIR/mongod.log" --fork
  MONGO_AUTH=0 MONGO_BIN="$MONGO_SHELL_BIN" \
    MONGODB_PASSWORD="$MONGODB_PASSWORD" \
    "$ROOT_DIR/scripts/demo/seed-local.sh"
  "$MONGO_BIN" --dbpath "$MONGO_DATA_DIR" --shutdown
  "$MONGO_BIN" --dbpath "$MONGO_DATA_DIR" --bind_ip 127.0.0.1 \
    --port 27017 --auth --logpath "$LOG_DIR/mongod.log" --fork
fi

start_service() {
  local name="$1"
  shift
  if [[ -f "$PID_DIR/$name.pid" ]] && kill -0 "$(cat "$PID_DIR/$name.pid")" 2>/dev/null; then
    return
  fi
  nohup env "$@" \
    "$JAVA_BIN" -Xmx192m -XX:MaxMetaspaceSize=128m \
    -jar "$ROOT_DIR/$name/target/$name.jar" \
    >"$LOG_DIR/$name.log" 2>&1 &
  echo "$!" >"$PID_DIR/$name.pid"
}

COMMON=(
  "CONFIG_SERVICE_PASSWORD=${CONFIG_SERVICE_PASSWORD:-password}"
  "SPRING_PROFILES_ACTIVE=local"
  "SPRING_CLOUD_CONFIG_URI=http://localhost:8888"
)

start_service config \
  "CONFIG_SERVICE_PASSWORD=${CONFIG_SERVICE_PASSWORD:-password}" \
  "SPRING_PROFILES_ACTIVE=local,native"

for _ in $(seq 1 120); do
  curl -fsS -u "user:${CONFIG_SERVICE_PASSWORD:-password}" \
    http://localhost:8888/application/local >/dev/null && break
  sleep 1
done

start_service registry "${COMMON[@]}"
start_service auth-service "${COMMON[@]}" \
  "MONGODB_PASSWORD=$MONGODB_PASSWORD"

for _ in $(seq 1 120); do
  if curl -fsS -H 'Accept: application/json' \
    http://localhost:8761/eureka/apps/AUTH-SERVICE |
    grep -q '"status":"UP"'; then
    break
  fi
  sleep 1
done

start_service account-service "${COMMON[@]}" \
  "MONGODB_PASSWORD=$MONGODB_PASSWORD" \
  "ACCOUNT_SERVICE_PASSWORD=${ACCOUNT_SERVICE_PASSWORD:-password}"
start_service statistics-service "${COMMON[@]}" \
  "MONGODB_PASSWORD=$MONGODB_PASSWORD" \
  "STATISTICS_SERVICE_PASSWORD=${STATISTICS_SERVICE_PASSWORD:-password}"
start_service notification-service "${COMMON[@]}" \
  "MONGODB_PASSWORD=$MONGODB_PASSWORD" \
  "NOTIFICATION_SERVICE_PASSWORD=${NOTIFICATION_SERVICE_PASSWORD:-password}"
start_service gateway "${COMMON[@]}"

for _ in $(seq 1 180); do
  if curl -fsS http://localhost:4000/accounts/demo |
    grep -q '"currency":"JPY"' &&
    curl -fsS -H 'Accept: application/json' \
      http://localhost:8761/eureka/apps/AUTH-SERVICE |
      grep -q '"status":"UP"' &&
    curl -fsS -H 'Accept: application/json' \
      http://localhost:8761/eureka/apps/ACCOUNT-SERVICE |
      grep -q '"status":"UP"'; then
    echo "T1 core stack is ready"
    exit 0
  fi
  sleep 1
done

echo "Timed out waiting for routed gateway readiness" >&2
exit 1
