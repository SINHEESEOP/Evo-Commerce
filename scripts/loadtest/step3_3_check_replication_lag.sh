#!/bin/bash
set -e

BASE_URL="http://localhost:8080"
TOKEN="$1"

if [ -z "$TOKEN" ]; then
  echo "usage: $0 <MASTER_ROLE_JWT_TOKEN>"
  exit 1
fi

WORKDIR=$(mktemp -d)
LAG_LOG="$WORKDIR/replication_lag.log"
POST_BODY="$WORKDIR/product_create.json"
echo '{"name":"loadtest-product","price":1000,"stock":100}' > "$POST_BODY"

poll_replication_lag() {
  while true; do
    LAG=$(docker exec evo-mysql-slave mysql -uroot -proot1234 -e "SHOW SLAVE STATUS\G" 2>/dev/null | awk '/Seconds_Behind_Master/ {print $2}')
    echo "$(date +%H:%M:%S.%3N) lag=${LAG:-NULL}" >> "$LAG_LOG"
    sleep 0.2
  done
}

echo "== Master 쓰기 부하(POST)와 Slave 조회 부하(GET) 동시 발생 =="
poll_replication_lag &
POLL_PID=$!
trap 'kill "$POLL_PID" 2>/dev/null' EXIT

ab -n 3000 -c 80 -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/products" > "$WORKDIR/read_result.txt" &
READ_PID=$!

ab -n 3000 -c 80 -p "$POST_BODY" -T "application/json" -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/products" > "$WORKDIR/write_result.txt" &
WRITE_PID=$!

wait "$READ_PID" "$WRITE_PID"
kill "$POLL_PID" 2>/dev/null
wait "$POLL_PID" 2>/dev/null || true

echo "== 조회(Slave) 처리량 =="
grep -E "Requests per second|Time per request" "$WORKDIR/read_result.txt"

echo "== 쓰기(Master) 처리량 =="
grep -E "Requests per second|Time per request" "$WORKDIR/write_result.txt"

echo "== 부하 구간 동안 관측된 복제 지연 최댓값 =="
MAX_LAG=$(grep -v NULL "$LAG_LOG" | awk -F'lag=' '{print $2}' | sort -n | tail -1)
echo "max Seconds_Behind_Master: ${MAX_LAG:-0}"
echo "전체 폴링 로그: $LAG_LOG"
