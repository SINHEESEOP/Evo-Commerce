#!/bin/bash
set -e

BASE_URL="http://localhost:8080"
TOKEN="$1"

if [ -z "$TOKEN" ]; then
  echo "usage: $0 <JWT_TOKEN>"
  exit 1
fi

echo "== 대량 조회 트래픽 발생 =="
ab -n 2000 -c 1 -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/products" > /tmp/ab_result.txt
grep -E "Requests per second|Time per request" /tmp/ab_result.txt

echo "== 복제 지연 확인 =="
docker exec evo-mysql-slave mysql -uroot -proot1234 -e "SHOW SLAVE STATUS\G" 2>/dev/null | grep "Seconds_Behind_Master"
