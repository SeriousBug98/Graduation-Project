#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
INGEST_PATH="${INGEST_PATH:-/api/ingest/log}"
EVENTS_PATH="${EVENTS_PATH:-/api/events}"
NORMAL_COUNT="${NORMAL_COUNT:-1000}"
ATTACK_COUNT="${ATTACK_COUNT:-50}"
NORMAL_SLEEP_MS="${NORMAL_SLEEP_MS:-50}"   # slower normals to avoid BEHAVIOR triggers
ATTACK_SLEEP_MS="${ATTACK_SLEEP_MS:-5}"
WAIT_BEFORE_ATTACK_MS="${WAIT_BEFORE_ATTACK_MS:-60000}"  # cool-down between phases
POLL_MS="${POLL_MS:-50}"
POLL_MAX="${POLL_MAX:-60}"
EVENT_TYPES="${EVENT_TYPES:-PATTERN,BEHAVIOR,AUTHZ}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: $1 not found" >&2; exit 1; }; }
need curl; need jq; python3 - <<<'import sys' >/dev/null 2>&1 || true

WORK_DIR="$(mktemp -d -t nft07v2_XXXXXXXX)"
NORMAL_IDS="$WORK_DIR/normal_ids.txt"
ATTACK_IDS="$WORK_DIR/attack_ids.txt"
touch "$NORMAL_IDS" "$ATTACK_IDS"
trap 'echo "Work dir: $WORK_DIR"' EXIT

rand_uuid() { cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen; }

sleep_ms() {
  local ms="${1:-0}"
  python3 - "$ms" <<'PY' 2>/dev/null || sleep 0.05
import sys, time
ms = float(sys.argv[1])
time.sleep(ms/1000.0)
PY
}

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
  curl -sS -X POST "$BASE$INGEST_PATH" -H "Content-Type: application/json" -d "$body"
}

# Poll strictly from /api/events. Require event.type ∈ EVENT_TYPES
# and prefer event.logId == given logId. If logId field absent, accept match by userId + recentness window.
poll_detect() {
  local logId="$1"
  local userId="$2"
  local tries=0
  local types_json
  types_json=$(jq -nc --arg csv "$EVENT_TYPES" '$csv|split(",")|map(ascii_upcase)')

  while (( tries < POLL_MAX )); do
    # Try filter by logId if API supports it
    local resp
    resp=$(curl -sS -G "$BASE$EVENTS_PATH" \
      --data-urlencode "logId=$logId" \
      --data-urlencode "size=5" 2>/dev/null || true)

    # Parse common shapes: {content:[...]}, or array root
    local ok="false"
    if [[ -n "$resp" ]]; then
      ok=$(echo "$resp" | jq -r --arg id "$logId" --argjson types "$types_json" '
        def up(s): (s//""|tostring|ascii_upcase);
        def acc(e): (
          ( (e.logId|tostring == $id) or (e.id|tostring == $id) ) and
          ( up(e.type) as $t | any($types[]; . == $t) )
        );
        if type=="object" and .content then any(.content[]?; acc(.)) else
        if type=="array" then any(.[]?; acc(.)) else false end end
      ' 2>/dev/null || echo "false")
    fi
    if [[ "$ok" == "true" ]]; then return 0; fi

    # Secondary attempt: filter by userId if backend lacks logId filter
    resp=$(curl -sS -G "$BASE$EVENTS_PATH" \
      --data-urlencode "user=$userId" \
      --data-urlencode "size=5" 2>/dev/null || true)
    if [[ -n "$resp" ]]; then
      ok=$(echo "$resp" | jq -r --argjson types "$types_json" '
        def up(s): (s//""|tostring|ascii_upcase);
        if type=="object" and .content then any(.content[]?; ( up(.type) as $t | any($types[]; . == $t) )) else
        if type=="array" then any(.[]?; ( up(.type) as $t | any($types[]; . == $t) )) else false end end
      ' 2>/dev/null || echo "false")
      if [[ "$ok" == "true" ]]; then return 0; fi
    fi

    tries=$((tries+1))
    sleep_ms "$POLL_MS"
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

echo "# Phase 1: NORMAL $NORMAL_COUNT posts (pacing ${NORMAL_SLEEP_MS}ms)"
for ((i=1;i<=NORMAL_COUNT;i++)); do
  sqlRaw="$(gen_normal_sql "$i")"
  sqlSummary="normal query $i"
  # Capture id + userId by echoing both as CSV
  uid="n-$(rand_uuid)@test.com"
  body=$(jq -nc --arg userId "$uid" --arg sqlRaw "$sqlRaw" --arg sqlSummary "$sqlSummary" '{
    executedAt: null, userId: $userId, sqlRaw: $sqlRaw, sqlSummary: $sqlSummary, returnRows: 0, status: "SUCCESS"
  }')
  resp="$(curl -sS -X POST "$BASE$INGEST_PATH" -H "Content-Type: application/json" -d "$body" || true)"
  id="$(echo "$resp" | jq -r '.id // empty' 2>/dev/null || true)"
  if [[ -n "$id" ]]; then echo "$id,$uid" >> "$NORMAL_IDS"; fi
  sleep_ms "$NORMAL_SLEEP_MS"
done
echo "Collected normals: $(wc -l < "$NORMAL_IDS" | tr -d ' ')"

if (( WAIT_BEFORE_ATTACK_MS > 0 )); then
  echo "# Cool-down ${WAIT_BEFORE_ATTACK_MS}ms before ATTACK phase..."
  sleep_ms "$WAIT_BEFORE_ATTACK_MS"
fi

echo "# Phase 2: ATTACK $ATTACK_COUNT posts (pacing ${ATTACK_SLEEP_MS}ms)"
ai=0
attack_sql_list | while IFS= read -r sql; do
  ((ai++)); ((ai>ATTACK_COUNT)) && break
  uid="a-$(rand_uuid)@test.com"
  body=$(jq -nc --arg userId "$uid" --arg sqlRaw "$sql" --arg sqlSummary "attack pattern $ai" '{
    executedAt: null, userId: $userId, sqlRaw: $sqlRaw, sqlSummary: $sqlSummary, returnRows: 0, status: "SUCCESS"
  }')
  resp="$(curl -sS -X POST "$BASE$INGEST_PATH" -H "Content-Type: application/json" -d "$body" || true)"
  id="$(echo "$resp" | jq -r '.id // empty' 2>/dev/null || true)"
  if [[ -n "$id" ]]; then echo "$id,$uid" >> "$ATTACK_IDS"; fi
  sleep_ms "$ATTACK_SLEEP_MS"
done
echo "Collected attacks: $(wc -l < "$ATTACK_IDS" | tr -d ' ')"

TP=0; FP=0; TN=0; FN=0
echo "# Poll detections (require real events; types: $EVENT_TYPES)"

while IFS=, read -r id uid; do
  [[ -z "${id:-}" ]] && continue
  if poll_detect "$id" "$uid"; then ((FP++)); else ((TN++)); fi
done < "$NORMAL_IDS"

while IFS=, read -r id uid; do
  [[ -z "${id:-}" ]] && continue
  if poll_detect "$id" "$uid"; then ((TP++)); else ((FN++)); fi
done < "$ATTACK_IDS"

normal_total=$((TN+FP))
attack_total=$((TP+FN))

fpr="0.00"; fnr="0.00"
if (( normal_total > 0 )); then fpr=$(python3 - <<PY
n=$normal_total; fp=$FP
print(f"{(fp*100.0)/n:.2f}")
PY
); fi
if (( attack_total > 0 )); then fnr=$(python3 - <<PY
a=$attack_total; fn=$FN
print(f"{(fn*100.0)/a:.2f}")
PY
); fi

echo "-------------------------------------------"
echo "CONFUSION MATRIX (strict events only)"
echo "TP=$TP  FP=$FP  TN=$TN  FN=$FN"
echo "NORMAL total=$normal_total  ATTACK total=$attack_total"
echo "FPR (<=5% target): $fpr%"
echo "FNR (<=2% target): $fnr%"
echo "Work dir: $WORK_DIR"
echo "-------------------------------------------"
