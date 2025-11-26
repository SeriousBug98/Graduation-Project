#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
INGEST_PATH="${INGEST_PATH:-/api/ingest/log}"
EVENTS_PATH="${EVENTS_PATH:-/api/events}"
NORMAL_COUNT="${NORMAL_COUNT:-1000}"
ATTACK_COUNT="${ATTACK_COUNT:-50}"
NORMAL_SLEEP_MS="${NORMAL_SLEEP_MS:-50}"
ATTACK_SLEEP_MS="${ATTACK_SLEEP_MS:-5}"
WAIT_BEFORE_ATTACK_MS="${WAIT_BEFORE_ATTACK_MS:-60000}"
POLL_MS="${POLL_MS:-20}"
POLL_MAX="${POLL_MAX:-5}"
EVENT_TYPES="${EVENT_TYPES:-PATTERN,AUTHZ}"
CONNECT_TIMEOUT="${CONNECT_TIMEOUT:-1}"
MAX_TIME="${MAX_TIME:-2}"
USE_LOGID="${USE_LOGID:-true}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: $1 not found" >&2; exit 1; }; }
need curl; need jq; python3 - <<<'import sys' >/dev/null 2>&1 || true

WORK_DIR="$(mktemp -d -t nft07v3_XXXXXXXX)"
NORMAL_IDS="$WORK_DIR/normal_ids.csv"
ATTACK_IDS="$WORK_DIR/attack_ids.csv"
touch "$NORMAL_IDS" "$ATTACK_IDS"
trap 'echo; echo "Work dir: $WORK_DIR"' EXIT

rand_uuid() { cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen; }
sleep_ms() { python3 - "$1" <<'PY' 2>/dev/null || sleep 0.05
import sys, time; time.sleep(float(sys.argv[1])/1000.0)
PY
}

post_log() {
  local user_prefix="$1"   # n or a
  local sqlRaw="$2"
  local sqlSummary="$3"
  local userId="${user_prefix}-$(rand_uuid)@test.com"
  local body
  body=$(jq -nc --arg userId "$userId" --arg sqlRaw "$sqlRaw" --arg sqlSummary "$sqlSummary" '{
    executedAt: null, userId: $userId, sqlRaw: $sqlRaw, sqlSummary: $sqlSummary, returnRows: 0, status: "SUCCESS"
  }')
  local resp
  resp=$(curl -sS --connect-timeout "$CONNECT_TIMEOUT" --max-time "$MAX_TIME"          -X POST "$BASE$INGEST_PATH" -H "Content-Type: application/json" -d "$body" || true)
  local id
  id="$(echo "$resp" | jq -r '.id // empty' 2>/dev/null || true)"
  echo "$id,$userId"
}

poll_detect() {
  local logId="$1"
  local userId="$2"
  local tries=0
  local types_json
  types_json=$(jq -nc --arg csv "$EVENT_TYPES" '$csv|split(",")|map(ascii_upcase)')
  local ok resp

  while (( tries < POLL_MAX )); do
    if [[ "$USE_LOGID" == "true" ]]; then
      resp=$(curl -sS --connect-timeout "$CONNECT_TIMEOUT" --max-time "$MAX_TIME"              -G "$BASE$EVENTS_PATH" --data-urlencode "logId=$logId" --data-urlencode "size=5" 2>/dev/null || true)
      if [[ -n "${resp:-}" ]]; then
        ok=$(echo "$resp" | jq -r --arg id "$logId" --argjson types "$types_json" '
          def up(x): (x//""|tostring|ascii_upcase);
          def t(e): up(e.eventType // e.type);
          def acc(e): ( ( (e.logId|tostring == $id) or (e.id|tostring == $id) ) and any($types[]; . == t(e)) );
          if type=="object" and .content then any(.content[]?; acc(.)) else
          if type=="array" then any(.[]?; acc(.)) else false end end
        ' 2>/dev/null || echo "false")
        [[ "$ok" == "true" ]] && return 0
      fi
    fi

    resp=$(curl -sS --connect-timeout "$CONNECT_TIMEOUT" --max-time "$MAX_TIME"            -G "$BASE$EVENTS_PATH" --data-urlencode "user=$userId" --data-urlencode "size=5" 2>/dev/null || true)
    if [[ -n "${resp:-}" ]]; then
      ok=$(echo "$resp" | jq -r --argjson types "$types_json" '
        def up(x): (x//""|tostring|ascii_upcase);
        def t(e): up(e.eventType // e.type);
        if type=="object" and .content then any(.content[]?; any($types[]; . == t(.))) else
        if type=="array" then any(.[]?; any($types[]; . == t(.))) else false end end
      ' 2>/dev/null || echo "false")
      [[ "$ok" == "true" ]] && return 0
    fi

    tries=$((tries+1))
    (( tries % 5 == 0 )) && printf "." 1>&2
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
  IFS=',' read -r id uid <<< "$(post_log "n" "$sqlRaw" "normal query $i")"
  if [[ -n "${id:-}" ]]; then echo "$id,$uid" >> "$NORMAL_IDS"; fi
  (( i % 100 == 0 )) && echo "  - posted normals: $i"
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
  IFS=',' read -r id uid <<< "$(post_log "a" "$sql" "attack pattern $ai")"
  if [[ -n "${id:-}" ]]; then echo "$id,$uid" >> "$ATTACK_IDS"; fi
  (( ai % 10 == 0 )) && echo "  - posted attacks: $ai"
  sleep_ms "$ATTACK_SLEEP_MS"
done
echo "Collected attacks: $(wc -l < "$ATTACK_IDS" | tr -d ' ')"

TP=0; FP=0; TN=0; FN=0
echo -n "# Poll detections (types=$EVENT_TYPES, logId=${USE_LOGID}) "
count=0

while IFS=, read -r id uid; do
  [[ -z "${id:-}" ]] && continue
  if poll_detect "$id" "$uid"; then ((FP++)); else ((TN++)); fi
  ((++count % 100 == 0)) && printf " N%03d" "$count"
done < "$NORMAL_IDS"

count=0
while IFS=, read -r id uid; do
  [[ -z "${id:-}" ]] && continue
  if poll_detect "$id" "$uid"; then ((TP++)); else ((FN++)); fi
  ((++count % 10 == 0)) && printf " A%03d" "$count"
done < "$ATTACK_IDS"
echo

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
echo "CONFUSION MATRIX (events only)"
echo "TP=$TP  FP=$FP  TN=$TN  FN=$FN"
echo "NORMAL total=$normal_total  ATTACK total=$attack_total"
echo "FPR (<=5% target): $fpr%"
echo "FNR (<=2% target): $fnr%"
echo "Work dir: $WORK_DIR"
echo "-------------------------------------------"
