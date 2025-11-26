#!/usr/bin/env bash
set -euo pipefail

# ====== Config (override via env) ======
BASE="${BASE:-http://localhost:8080}"
INGEST_PATH="${INGEST_PATH:-/api/ingest/log}"
EVENTS_PATH="${EVENTS_PATH:-/api/events}"   # Fallback to /api/logs if /api/events not available (see below)
NORMAL_COUNT="${NORMAL_COUNT:-1000}"
ATTACK_COUNT="${ATTACK_COUNT:-50}"
POLL_MS="${POLL_MS:-50}"    # sleep milliseconds between polls
POLL_MAX="${POLL_MAX:-60}"  # number of polls per id (e.g., 60 * 50ms = 3s max)
THREADS="${THREADS:-1}"     # reserved

# ====== Dependencies check ======
for cmd in curl jq; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: '$cmd' not found. Please install it." >&2
    exit 1
  fi
done

# ====== Working files ======
WORK_DIR="$(mktemp -d -t nft07_XXXXXXXX)"
NORMAL_IDS="$WORK_DIR/normal_ids.txt"
ATTACK_IDS="$WORK_DIR/attack_ids.txt"
RESULT_LOG="$WORK_DIR/result.log"
touch "$NORMAL_IDS" "$ATTACK_IDS" "$RESULT_LOG"

cleanup() {
  echo "Work dir: $WORK_DIR"
}
trap cleanup EXIT

# ====== Helpers ======
rand_uuid() { cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen; }

post_log() {
  local user_prefix="$1"   # n or a
  local sqlRaw="$2"
  local sqlSummary="$3"

  local userId="${user_prefix}-$(rand_uuid)@test.com"
  local body
  body=$(jq -nc --arg userId "$userId" --arg sqlRaw "$sqlRaw" --arg sqlSummary "$sqlSummary" '{
    executedAt: null,
    userId: $userId,
    sqlRaw: $sqlRaw,
    sqlSummary: $sqlSummary,
    returnRows: 0,
    status: "SUCCESS"
  }')

  curl -sS -X POST "$BASE$INGEST_PATH" \
    -H "Content-Type: application/json" \
    -d "$body"
}

poll_detect() {
  local logId="$1"
  local tries=0

  while (( tries < POLL_MAX )); do
    local resp status
    resp=$(curl -sS -G "$BASE$EVENTS_PATH" --data-urlencode "logId=$logId" --data-urlencode "size=1" || true)
    status=$?
    if [[ $status -eq 0 && -n "$resp" ]]; then
      local cnt=0
      cnt=$(echo "$resp" | jq -r '(.content // []) | length' 2>/dev/null || echo "0")
      if [[ "$cnt" =~ ^[0-9]+$ ]]; then
        if (( cnt > 0 )); then return 0; fi
      else
        cnt=$(echo "$resp" | jq -r 'length' 2>/dev/null || echo "0")
        if [[ "$cnt" =~ ^[0-9]+$ ]] && (( cnt > 0 )); then return 0; fi
      fi
    fi

    local logs_resp
    logs_resp=$(curl -sS -G "$BASE/api/logs" --data-urlencode "page=0" --data-urlencode "size=20" --data-urlencode "sort=executedAt,DESC" || true)
    if echo "$logs_resp" | jq -e --arg id "$logId" '.content | any(.id|tostring == $id)' >/dev/null 2>&1; then
      return 0
    fi

    tries=$((tries+1))
    python3 - <<PY 2>/dev/null || sleep 0.05
import time; time.sleep(${POLL_MS}/1000.0)
PY
  done
  return 1
}

gen_normal_sql() {
  local i="$1"
  case $(( i % 10 )) in
    0) echo "SELECT * FROM orders WHERE id=$i" ;;
    1) echo "SELECT * FROM users WHERE id=$i" ;;
    2) echo "SELECT * FROM products WHERE id=$i" ;;
    3) echo "SELECT count(*) FROM orders WHERE id>$i" ;;
    4) echo "SELECT name FROM users WHERE id=$i" ;;
    5) echo "SELECT price FROM products WHERE id=$i" ;;
    6) echo "SELECT * FROM payments WHERE order_id=$i" ;;
    7) echo "SELECT * FROM shipments WHERE order_id=$i" ;;
    8) echo "SELECT * FROM reviews WHERE product_id=$i" ;;
    9) echo "SELECT * FROM inventory WHERE product_id=$i" ;;
  esac
}

attack_sql_list() {
cat <<'SQLS'
DROP TABLE important_table
DROP TABLE users
DROP TABLE orders
DELETE FROM users WHERE 1=1
DELETE FROM orders WHERE 1=1
UPDATE users SET role='admin' WHERE id=1
UPDATE accounts SET balance=0 WHERE id=1
SELECT * FROM users WHERE username='' OR '1'='1'
SELECT * FROM orders WHERE 'x'='x'
SELECT * FROM information_schema.tables
SELECT * FROM information_schema.columns
UNION SELECT null
UNION SELECT 1
UNION SELECT password_hash FROM users
UNION SELECT api_key FROM secrets
' OR '1'='1
' OR 1=1 --
admin' --
admin' #
admin' or '1'='1
1; DROP TABLE sessions
1; DELETE FROM sessions
1; UPDATE users SET role='admin' WHERE id=1
SELECT load_file('/etc/passwd')
SELECT pg_sleep(5)
SELECT sleep(5)
SELECT 1 FROM dual WHERE 1=pg_sleep(3)
xp_cmdshell 'dir'
CALL shutdown()
SELECT * FROM mysql.user
SELECT * FROM pg_catalog.pg_user
COPY secrets TO '/tmp/leak.txt'
BULK INSERT users FROM '/etc/passwd'
ALTER TABLE users DROP COLUMN password
ALTER TABLE orders DROP COLUMN total
GRANT ALL ON objects TO public
REVOKE ALL ON users FROM admin
TRUNCATE TABLE logs
TRUNCATE TABLE sessions
CREATE USER attacker WITH PASSWORD 'pw'
CREATE FUNCTION evil RETURNS void LANGUAGE plpgsql AS $$ BEGIN END $$
CREATE EXTENSION file_fdw
DROP DATABASE prod
ALTER SYSTEM SET shared_buffers='0'
SET maintenance_mode=on
INSERT INTO admin(email,password) VALUES('a@a','p')
LOCK TABLE users
LOCK TABLE orders
ANALYZE VERBOSE users
VACUUM FULL
SQLS
}

echo "Posting NORMAL $NORMAL_COUNT logs to $BASE$INGEST_PATH ..."
for ((i=1;i<=NORMAL_COUNT;i++)); do
  sqlRaw="$(gen_normal_sql "$i")"
  sqlSummary="normal query $i"
  resp="$(post_log "n" "$sqlRaw" "$sqlSummary" || true)"
  id="$(echo "$resp" | jq -r '.id // empty' 2>/dev/null || true)"
  if [[ -n "$id" ]]; then
    echo "$id" >> "$NORMAL_IDS"
  else
    echo "WARN normal[$i]: no id in response: $resp" >> "$RESULT_LOG"
  fi
  sleep 0.005
done
normal_total="$(wc -l < "$NORMAL_IDS" | tr -d ' ')"
echo "Collected NORMAL ids: $normal_total"

echo "Posting ATTACK $ATTACK_COUNT logs to $BASE$INGEST_PATH ..."
ai=0
attack_sql_list | while IFS= read -r sql; do
  ((ai++))
  ((ai>ATTACK_COUNT)) && break
  resp="$(post_log "a" "$sql" "attack pattern $ai" || true)"
  id="$(echo "$resp" | jq -r '.id // empty' 2>/dev/null || true)"
  if [[ -n "$id" ]]; then
    echo "$id" >> "$ATTACK_IDS"
  else
    echo "WARN attack[$ai]: no id in response: $resp" >> "$RESULT_LOG"
  fi
  sleep 0.005
done
attack_total="$(wc -l < "$ATTACK_IDS" | tr -d ' ')"
echo "Collected ATTACK ids: $attack_total"

TP=0; FP=0; TN=0; FN=0
echo "Polling detections (per id up to $POLL_MAX tries * ${POLL_MS}ms)..."

while IFS= read -r id; do
  [[ -z "$id" ]] && continue
  if poll_detect "$id"; then
    ((FP++))
  else
    ((TN++))
  fi
done < "$NORMAL_IDS"

while IFS= read -r id; do
  [[ -z "$id" ]] && continue
  if poll_detect "$id"; then
    ((TP++))
  else
    ((FN++))
  fi
done < "$ATTACK_IDS"

normal_total=$((TN+FP))
attack_total=$((TP+FN))

fpr="0.00"
fnr="0.00"
if (( normal_total > 0 )); then
  fpr=$(python3 - <<PY
n=$normal_total; fp=$FP
print(f"{(fp*100.0)/n:.2f}")
PY
)
fi
if (( attack_total > 0 )); then
  fnr=$(python3 - <<PY
a=$attack_total; fn=$FN
print(f"{(fn*100.0)/a:.2f}")
PY
)
fi

echo "-------------------------------------------"
echo "CONFUSION MATRIX"
echo "TP=$TP  FP=$FP  TN=$TN  FN=$FN"
echo "NORMAL total=$normal_total  ATTACK total=$attack_total"
echo "FPR (<=5% target): $fpr%"
echo "FNR (<=2% target): $fnr%"
echo "-------------------------------------------"
